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
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Cancelación de una reserva CONFIRMADA por su huésped titular o por el anfitrión de la publicación
 * (SOF-75, HU-30, RF-46/RF-48). El valor de devolución es siempre el 100% del total de la reserva: el
 * cálculo por política de cancelación (porcentajes parciales según anticipación) es SOF-76, fuera de
 * alcance acá. La auditoría distingue quién canceló (AC3); huésped y anfitrión reciben la notificación
 * (SOF-37).
 */
@Service
public class CancelacionReservaService {

    private static final String ENTIDAD = "Reserva";
    // "Hoy" es el día en Colombia, igual que en ReservaService.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final ReservaRepository reservas;
    private final PublicacionService publicaciones;
    private final RegistroReservas registro;
    private final PublicadorEventos publicador;
    private final NotificacionesReserva notificaciones;
    private final Clock reloj;

    public CancelacionReservaService(ReservaRepository reservas, PublicacionService publicaciones,
            RegistroReservas registro, PublicadorEventos publicador, NotificacionesReserva notificaciones,
            Clock reloj) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.registro = registro;
        this.publicador = publicador;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    /**
     * Cancela la reserva CONFIRMADA de {@code usuarioId}, que debe ser su huésped titular o el anfitrión de
     * la publicación (AC1, AC3); cualquier otro usuario recibe 403 y el intento queda auditado (AC5). Una
     * estancia que ya terminó, o una reserva que dejó de estar CONFIRMADA, responde 409 sin registrar la
     * cancelación (AC4).
     */
    public Cancelacion cancelar(Long reservaId, Long usuarioId, String motivo) {
        Reserva reserva = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        boolean esHuesped = reserva.getHuespedId().equals(usuarioId);
        boolean esAnfitrion = !esHuesped && esAnfitrionDeLaPublicacion(reserva.getPublicacionId(), usuarioId);
        if (!esHuesped && !esAnfitrion) {
            auditar(reservaId, usuarioId, ResultadoEvento.FALLO, Map.of("resultado", "intento_no_autorizado"));
            throw new AccesoDenegadoException();
        }
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new ReservaNoCancelableException(mensajeEstadoNoCancelable(reserva.getEstado()));
        }
        if (!LocalDate.now(reloj.withZone(ZONA)).isBefore(reserva.getSalida())) {
            throw new ReservaNoCancelableException("La estancia ya terminó: no se puede cancelar.");
        }

        BigDecimal valorDevolucion = reserva.getTotal();
        Cancelacion cancelacion = Cancelacion.nueva(reservaId, usuarioId, Instant.now(reloj), motivo,
                valorDevolucion, EstadoReserva.CANCELADA);
        // El cambio de estado y el registro de la cancelación son una sola transacción (RegistroReservas).
        if (!registro.cancelar(cancelacion)) {
            // Perdió la carrera: otra cancelación, o una conciliación de pago, la resolvió justo antes.
            Reserva actual = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
            throw new ReservaNoCancelableException(mensajeEstadoNoCancelable(actual.getEstado()));
        }

        auditar(reservaId, usuarioId, ResultadoEvento.EXITO, Map.of(
                "canceladoPor", esAnfitrion ? "ANFITRION" : "HUESPED",
                "motivo", motivo,
                "valorDevolucion", valorDevolucion.toPlainString()));
        notificaciones.notificar(reserva, TipoNotificacion.RESERVA_CANCELADA);
        return cancelacion;
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
        };
    }

    private void auditar(Long reservaId, Long usuarioId, ResultadoEvento resultado, Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", "cancelar");
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), AccionCritica.CANCELACION, ENTIDAD,
                String.valueOf(reservaId), resultado, Instant.now(reloj), contexto));
    }
}
