package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.compartido.seguridad.Propiedad;
import co.edu.unbosque.marketplace.reservas.exceptions.ApartadoPerdidoDuranteElPagoException;
import co.edu.unbosque.marketplace.reservas.exceptions.FalloPasarelaPagoException;
import co.edu.unbosque.marketplace.reservas.exceptions.PagoRechazadoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoPagableException;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroAprobado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroRechazado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.ResultadoCobro;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.SolicitudCobro;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pago de una reserva PENDIENTE con la pasarela en modo prueba (SOF-34, RF-37). La llamada a la pasarela va
 * fuera de toda transacción, para no retener una conexión mientras espera; cada resultado se guarda luego en
 * su propia transacción de {@link RegistroReservas}, con una actualización condicional: si la reserva dejó de
 * estar PENDIENTE mientras se cobraba (venció el apartado u otro intento ganó), el cobro se reembolsa.
 *
 * <p>Aprobado: CONFIRMADA con la transacción. Rechazado: CANCELADA, lo que libera las noches. Sin respuesta o
 * falla de la pasarela: sigue PENDIENTE con el pago en ERROR, que es un estado conocido (vence con su
 * apartado como cualquier otra), y se puede reintentar; la clave de idempotencia por reserva evita cobrar dos
 * veces si el primer intento sí llegó a cobrarse.
 */
@Service
public class PagoReservaService {

    private static final Logger LOG = LoggerFactory.getLogger(PagoReservaService.class);
    private static final String ENTIDAD = "Reserva";

    private final ReservaRepository reservas;
    private final RegistroReservas registro;
    private final PasarelaPagos pasarela;
    private final PublicadorEventos publicador;
    private final NotificacionesReserva notificaciones;
    private final Clock reloj;

    public PagoReservaService(ReservaRepository reservas, RegistroReservas registro, PasarelaPagos pasarela,
            PublicadorEventos publicador, NotificacionesReserva notificaciones, Clock reloj) {
        this.reservas = reservas;
        this.registro = registro;
        this.pasarela = pasarela;
        this.publicador = publicador;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    /**
     * Cobra la reserva del huésped con el método de prueba. Solo el dueño paga, y solo una reserva PENDIENTE
     * con el apartado vigente: una ya pagada, cancelada o vencida responde 409 sin llamar a la pasarela.
     */
    public Reserva pagar(Long reservaId, Long huespedId, String metodoPago) {
        Reserva reserva = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        Propiedad.exigirPropietario(reserva.getHuespedId(), huespedId);
        // EN_REVISION_MANUAL (SOF-83) es una CONFIRMADA cuya publicación se bloqueó: también está pagada.
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA || reserva.getEstado() == EstadoReserva.EN_REVISION_MANUAL) {
            throw new ReservaNoPagableException("Esta reserva ya está pagada.");
        }
        if (!reserva.ocupaNoches(Instant.now(reloj))) {
            throw new ReservaNoPagableException("Esta reserva ya no se puede pagar: su apartado venció o fue cancelada.");
        }

        // Una sola clave por reserva: si un cobro se completó en la pasarela después de agotarse la espera, el
        // reintento recibe ese mismo cobro y no abre otro. Con otra tarjeta la pasarela rechaza la clave en vez
        // de cobrar dos veces, por eso la pantalla deja fija la tarjeta tras un 504 o un 502.
        SolicitudCobro solicitud = new SolicitudCobro(reservaId, reserva.getTotal(), metodoPago,
                reserva.referenciaPago());
        ResultadoCobro resultado;
        try {
            resultado = pasarela.cobrar(solicitud);
        } catch (FallaPasarelaException falla) {
            registro.registrarErrorPago(reservaId);
            LOG.error("La pasarela no dio resultado al cobrar la reserva {}: {}", reservaId, falla.getMessage(), falla);
            auditar(reservaId, huespedId, ResultadoEvento.FALLO,
                    Map.of("resultado", falla.isSinRespuesta() ? "sin_respuesta" : "falla_pasarela"));
            throw new FalloPasarelaPagoException(falla.isSinRespuesta());
        }
        return switch (resultado) {
            case CobroAprobado aprobado -> confirmar(reservaId, huespedId, aprobado.transaccionId());
            case CobroRechazado rechazado -> rechazar(reserva, huespedId, rechazado.motivo());
        };
    }

    private Reserva confirmar(Long reservaId, Long huespedId, String transaccionId) {
        if (registro.confirmarPago(reservaId, transaccionId)) {
            auditar(reservaId, huespedId, ResultadoEvento.EXITO,
                    Map.of("resultado", "aprobado", "transaccionId", transaccionId));
            Reserva confirmada = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
            notificaciones.notificar(confirmada, TipoNotificacion.RESERVA_CONFIRMADA);
            return confirmada;
        }
        // Dos intentos simultáneos con el mismo método comparten la clave de idempotencia y la pasarela les
        // devuelve la misma transacción: si el otro ya confirmó con ella, este cobro es ese mismo, no otro.
        Reserva actual = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (transaccionId.equals(actual.getTransaccionId())) {
            return actual;
        }
        // Se cobró, pero la reserva ya no estaba PENDIENTE: el huésped no puede quedar con un cobro sin reserva.
        boolean reembolsado = reembolsar(reservaId, transaccionId);
        auditar(reservaId, huespedId, ResultadoEvento.FALLO, Map.of("resultado", "apartado_perdido",
                "transaccionId", transaccionId, "reembolsado", reembolsado));
        throw new ApartadoPerdidoDuranteElPagoException(reembolsado);
    }

    private boolean reembolsar(Long reservaId, String transaccionId) {
        try {
            pasarela.reembolsar(transaccionId);
            LOG.warn("Se reembolsó la transacción {}: la reserva {} dejó de estar pendiente durante el cobro",
                    transaccionId, reservaId);
            return true;
        } catch (FallaPasarelaException falla) {
            // En ERROR, la conciliación vuelve a intentar el reembolso hasta lograrlo.
            registro.registrarErrorPago(reservaId);
            LOG.error("No se pudo reembolsar la transacción {} de la reserva {}: queda para la conciliación",
                    transaccionId, reservaId, falla);
            return false;
        }
    }

    private Reserva rechazar(Reserva reserva, Long huespedId, String motivo) {
        Long reservaId = reserva.getId();
        if (!registro.rechazarPago(reservaId)) {
            // Otro intento ya la resolvió mientras este se rechazaba: no se informa que las fechas quedaron libres.
            Reserva actual = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
            throw new ReservaNoPagableException(actual.getEstado() == EstadoReserva.CONFIRMADA
                    ? "Esta reserva ya está pagada."
                    : "Esta reserva ya no se puede pagar: su apartado venció o fue cancelada.");
        }
        auditar(reservaId, huespedId, ResultadoEvento.FALLO, Map.of("resultado", "rechazado", "motivo", motivo));
        notificaciones.notificar(reserva, TipoNotificacion.RESERVA_RECHAZADA);
        throw new PagoRechazadoException(motivo);
    }

    private void auditar(Long reservaId, Long huespedId, ResultadoEvento resultado, Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", "pagar");
        publicador.publicar(new EventoCritico(String.valueOf(huespedId), AccionCritica.RESERVA, ENTIDAD,
                String.valueOf(reservaId), resultado, Instant.now(reloj), contexto));
    }
}
