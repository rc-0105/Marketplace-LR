package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.reservas.exceptions.ReembolsoFallidoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ResolucionInvalidaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoResolubleException;
import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisión manual de las reservas de una publicación bloqueada (SOF-83, AC3): el administrador las lista y
 * decide si cada una queda CONFIRMADA o CANCELADA con la devolución completa. La devolución es real (S7): la
 * pasarela devuelve el cobro antes de cancelar, y si falla nada cambia y el administrador reintenta.
 *
 * <p>No es transaccional: la llamada a la pasarela queda fuera de toda transacción y la escritura es una sola
 * transacción de {@link RegistroReservas}.
 */
@Service
public class AdministracionReservasService {

    private static final Logger LOG = LoggerFactory.getLogger(AdministracionReservasService.class);
    private static final String ENTIDAD = "Reserva";
    private static final String CONFIRMAR = "CONFIRMAR";
    private static final String CANCELAR = "CANCELAR";

    private final ReservaRepository reservas;
    private final PublicacionService publicaciones;
    private final RegistroReservas registro;
    private final PasarelaPagos pasarela;
    private final PublicadorEventos publicador;
    private final NotificacionesReserva notificaciones;
    private final Clock reloj;

    public AdministracionReservasService(ReservaRepository reservas, PublicacionService publicaciones,
            RegistroReservas registro, PasarelaPagos pasarela, PublicadorEventos publicador,
            NotificacionesReserva notificaciones, Clock reloj) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.registro = registro;
        this.pasarela = pasarela;
        this.publicador = publicador;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    /** Las reservas EN_REVISION_MANUAL por fecha de llegada, con el título y el motivo del bloqueo. */
    @Transactional(readOnly = true)
    public List<ReservaEnRevision> listarEnRevision() {
        List<Reserva> enRevision = reservas.findByEstadoOrderByLlegadaAscIdAsc(EstadoReserva.EN_REVISION_MANUAL);
        if (enRevision.isEmpty()) {
            return List.of();
        }
        Map<Long, AlojamientoReservado> alojamientos = publicaciones.consultarReservados(
                enRevision.stream().map(Reserva::getPublicacionId).collect(Collectors.toSet()));
        return enRevision.stream()
                .map(reserva -> new ReservaEnRevision(reserva, alojamientos.get(reserva.getPublicacionId())))
                .toList();
    }

    /**
     * Resuelve una reserva EN_REVISION_MANUAL: {@code CONFIRMAR} la devuelve a CONFIRMADA; {@code CANCELAR}
     * (con motivo) devuelve el cobro en la pasarela y la cancela con devolución del total. Huésped y anfitrión
     * reciben el aviso. 404 si no existe, 409 si no está en revisión, 502 si la pasarela no devolvió el cobro.
     */
    public ResolucionRevision resolver(Long reservaId, Long administradorId, String decision, String motivo) {
        boolean cancela = CANCELAR.equals(decision);
        if (cancela && (motivo == null || motivo.isBlank())) {
            throw new ResolucionInvalidaException("motivo", "El motivo es obligatorio para cancelar.");
        }
        if (!cancela && !CONFIRMAR.equals(decision)) {
            throw new ResolucionInvalidaException("decision", "La decisión debe ser CONFIRMAR o CANCELAR.");
        }
        Reserva reserva = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() != EstadoReserva.EN_REVISION_MANUAL) {
            throw new ReservaNoResolubleException();
        }
        return cancela ? cancelar(reserva, administradorId, motivo.trim()) : confirmar(reserva, administradorId);
    }

    private ResolucionRevision confirmar(Reserva reserva, Long administradorId) {
        if (!registro.confirmarRevision(reserva.getId())) {
            throw new ReservaNoResolubleException();
        }
        auditar(reserva.getId(), administradorId, Map.of("decision", CONFIRMAR, "reembolsado", false));
        notificaciones.notificar(reserva, TipoNotificacion.RESERVA_CONFIRMADA);
        return new ResolucionRevision(reserva.getId(), EstadoReserva.CONFIRMADA, false, null);
    }

    private ResolucionRevision cancelar(Reserva reserva, Long administradorId, String motivo) {
        Long reservaId = reserva.getId();
        String transaccionId = reserva.getTransaccionId();
        boolean reembolsado = transaccionId != null;
        if (reembolsado) {
            try {
                pasarela.reembolsar(transaccionId);
            } catch (FallaPasarelaException falla) {
                LOG.warn("La pasarela no devolvió la transacción {} de la reserva {} en revisión: sigue en revisión",
                        transaccionId, reservaId, falla);
                throw new ReembolsoFallidoException();
            }
        }

        BigDecimal valorDevolucion = reserva.getTotal();
        Cancelacion cancelacion = Cancelacion.nueva(reservaId, administradorId, Instant.now(reloj), motivo,
                valorDevolucion, EstadoReserva.CANCELADA);
        boolean cancelada;
        try {
            cancelada = registro.cancelarRevision(cancelacion, reembolsado);
        } catch (RuntimeException e) {
            if (reembolsado) {
                LOG.error("La pasarela devolvió la transacción {} pero la reserva {} no quedó cancelada: el dinero ya "
                        + "fue devuelto, revisar a mano", transaccionId, reservaId, e);
            }
            throw e;
        }
        if (!cancelada) {
            if (reembolsado) {
                LOG.error("La pasarela devolvió la transacción {} pero otra resolución cambió la reserva {} primero: "
                        + "el dinero ya fue devuelto, revisar a mano", transaccionId, reservaId);
            }
            throw new ReservaNoResolubleException();
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("decision", CANCELAR);
        datos.put("reembolsado", reembolsado);
        datos.put("motivo", motivo);
        datos.put("valorDevolucion", valorDevolucion.toPlainString());
        auditar(reservaId, administradorId, datos);
        notificaciones.notificar(reserva, TipoNotificacion.RESERVA_CANCELADA);
        return new ResolucionRevision(reservaId, EstadoReserva.CANCELADA, reembolsado, valorDevolucion);
    }

    private void auditar(Long reservaId, Long administradorId, Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", "resolver_revision");
        publicador.publicar(new EventoCritico(String.valueOf(administradorId), AccionCritica.ACCION_ADMINISTRATIVA,
                ENTIDAD, String.valueOf(reservaId), ResultadoEvento.EXITO, Instant.now(reloj), contexto));
    }
}
