package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoCancelableException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReembolsosSimulados;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Cancelación de una reserva CONFIRMADA por su huésped titular o por el anfitrión de la publicación
 * con los términos de la reserva. Solo el ganador de la transición solicita la devolución.
 */
@Service
public class CancelacionReservaService {

    private static final String ENTIDAD = "Reserva";
    private final ReembolsosSimulados reembolsos;

    private final ReservaRepository reservas;
    private final PublicacionService publicaciones;
    private final RegistroReservas registro;
    private final PublicadorEventos publicador;
    private final NotificacionesReserva notificaciones;
    private final Clock reloj;

    public CancelacionReservaService(ReservaRepository reservas, PublicacionService publicaciones,
            RegistroReservas registro, PublicadorEventos publicador, NotificacionesReserva notificaciones,
            Clock reloj, ReembolsosSimulados reembolsos) {
        this.reembolsos = reembolsos;
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.registro = registro;
        this.publicador = publicador;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    private record Evaluacion(Reserva reserva, boolean anfitrion, Instant fecha, CalculoCancelacion calculo,
            boolean solicitud) {}

    public CalculoCancelacion anticipar(Long reservaId, Long usuarioId) {
        return evaluar(reservaId, usuarioId).calculo();
    }

    private Evaluacion evaluar(Long reservaId, Long usuarioId) {
        Reserva reserva = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        boolean esHuesped = reserva.getHuespedId().equals(usuarioId);
        boolean esAnfitrion = !esHuesped && esAnfitrionDeLaPublicacion(reserva.getPublicacionId(), usuarioId);
        if (!esHuesped && !esAnfitrion) {
            auditar(reservaId, usuarioId, ResultadoEvento.FALLO, Map.of("resultado", "intento_no_autorizado"));
            throw new AccesoDenegadoException();
        }
        // SOF-79: el huésped retira su solicitud pendiente sin reembolso (nunca se cobró); el anfitrión la responde
        // aprobando o rechazando, no cancelando.
        boolean solicitud = reserva.getEstado() == EstadoReserva.PENDIENTE_APROBACION;
        if (solicitud && esAnfitrion) {
            throw new ReservaNoCancelableException("Esta solicitud espera tu respuesta: apruébala o recházala.");
        }
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA && !solicitud) {
            throw new ReservaNoCancelableException(mensajeEstadoNoCancelable(reserva.getEstado()));
        }
        Instant ahora = reloj.instant();
        if (!ahora.atZone(CalculoCancelacion.ZONA).toLocalDate().isBefore(reserva.getSalida())) {
            throw new ReservaNoCancelableException("La estancia ya terminó: no se puede cancelar.");
        }

        // Una solicitud nunca se cobró: la política de devolución no aplica y el cálculo siempre es 0.
        CalculoCancelacion calculo = solicitud
                ? new CalculoCancelacion(reserva.getPoliticaNombre(), reserva.getTotal(), 0,
                        BigDecimal.ZERO.setScale(2))
                : CalculoCancelacion.calcular(reserva.getPoliticaNombre(), reserva.getPoliticaDiasSinCosto(),
                        reserva.getPoliticaPorcentajeDevolucion(), reserva.getLlegada(), ahora, reserva.getTotal(),
                        esAnfitrion);
        return new Evaluacion(reserva, esAnfitrion, ahora, calculo, solicitud);
    }

    public Cancelacion cancelar(Long reservaId, Long usuarioId, String motivo) {
        Evaluacion evaluacion = evaluar(reservaId, usuarioId);
        BigDecimal valorDevolucion = evaluacion.calculo().valorDevolucion();
        Cancelacion cancelacion = Cancelacion.nueva(reservaId, usuarioId, evaluacion.fecha(), motivo,
                valorDevolucion, EstadoReserva.CANCELADA);
        cancelacion.prepararReembolsoSimulado();
        // El cambio de estado y el registro de la cancelación son una sola transacción (RegistroReservas).
        boolean cancelada = evaluacion.solicitud() ? registro.cancelarSolicitud(cancelacion) : registro.cancelar(cancelacion);
        if (!cancelada) {
            // Perdió la carrera: otra cancelación, o una conciliación de pago, la resolvió justo antes.
            Reserva actual = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
            throw new ReservaNoCancelableException(mensajeEstadoNoCancelable(actual.getEstado()));
        }

        if (valorDevolucion.signum() > 0) {
            try {
                cancelacion.registrarExito(reembolsos.solicitar(reservaId, valorDevolucion));
            } catch (RuntimeException errorPasarela) {
                cancelacion.registrarFallo();
            }
            registro.guardarResultado(cancelacion);
        }
        auditar(reservaId, usuarioId, ResultadoEvento.EXITO, Map.of(
                "canceladoPor", evaluacion.anfitrion() ? "ANFITRION" : "HUESPED",
                "motivo", motivo,
                "valorDevolucion", valorDevolucion.toPlainString()));
        notificaciones.notificar(evaluacion.reserva(), TipoNotificacion.RESERVA_CANCELADA);
        return cancelacion;
    }

    /** "Hoy" en Colombia según el reloj de la aplicación: la regla de la cancelación y de la revisión manual. */
    static LocalDate hoyEnColombia(Clock reloj) {
        return LocalDate.now(reloj.withZone(CalculoCancelacion.ZONA));
    }

    private boolean esAnfitrionDeLaPublicacion(Long publicacionId, Long usuarioId) {
        return publicaciones.consultarAcceso(publicacionId).map(acceso -> acceso.esDe(usuarioId)).orElse(false);
    }

    private String mensajeEstadoNoCancelable(EstadoReserva estado) {
        return switch (estado) {
            case CANCELADA -> "Esta reserva ya está cancelada.";
            case VENCIDA -> "Esta reserva ya venció: no se puede cancelar.";
            case PENDIENTE -> "Esta reserva todavía no está confirmada: no se puede cancelar.";
            case CONFIRMADA -> "Esta reserva ya no se puede cancelar.";
            case EN_REVISION_MANUAL -> "Esta reserva está en revisión por un administrador.";
            case PENDIENTE_APROBACION -> "Esta solicitud espera la aprobación del anfitrión.";
            case RECHAZADA -> "Esta solicitud fue rechazada por el anfitrión: no se puede cancelar.";
            case COMPLETADA -> "Esta reserva ya se completó: no se puede cancelar.";
        };
    }

    private void auditar(Long reservaId, Long usuarioId, ResultadoEvento resultado, Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", "cancelar");
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), AccionCritica.CANCELACION, ENTIDAD,
                String.valueOf(reservaId), resultado, Instant.now(reloj), contexto));
    }
}
