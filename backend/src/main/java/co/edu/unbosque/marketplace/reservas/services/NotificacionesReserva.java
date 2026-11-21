package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Avisa al huésped y al anfitrión que una reserva se confirmó, se rechazó o se canceló, con el alojamiento y
 * las fechas (SOF-37, AC1). Se llama solo después de que el cambio de estado quedó guardado, y nunca lanza:
 * si no se puede armar el aviso, la operación de la reserva ya está hecha y el aviso solo queda en el log.
 */
@Component
public class NotificacionesReserva {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionesReserva.class);
    private static final String ENTIDAD = "Reserva";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PublicacionService publicaciones;
    private final Notificador notificador;

    public NotificacionesReserva(PublicacionService publicaciones, Notificador notificador) {
        this.publicaciones = publicaciones;
        this.notificador = notificador;
    }

    public void notificar(Reserva reserva, TipoNotificacion tipo) {
        try {
            Long publicacionId = reserva.getPublicacionId();
            AlojamientoReservado alojamiento = publicaciones.consultarReservados(Set.of(publicacionId))
                    .get(publicacionId);
            if (alojamiento == null) {
                LOG.warn("No se notificó la reserva {}: su publicación {} ya no existe", reserva.getId(), publicacionId);
                return;
            }
            String detalle = "en «%s» del %s al %s".formatted(alojamiento.titulo(), reserva.getLlegada().format(FECHA),
                    reserva.getSalida().format(FECHA));
            notificador.notificar(new NuevaNotificacion(reserva.getHuespedId(), tipo, paraHuesped(tipo, detalle),
                    ENTIDAD, reserva.getId()));
            Optional<Long> anfitrion = publicaciones.consultarAcceso(publicacionId).map(AccesoPublicacion::anfitrionId);
            anfitrion.ifPresent(anfitrionId -> notificador.notificar(new NuevaNotificacion(anfitrionId, tipo,
                    paraAnfitrion(tipo, detalle), ENTIDAD, reserva.getId())));
        } catch (RuntimeException e) {
            LOG.error("No se pudo notificar la reserva {}: tipo={}", reserva.getId(), tipo, e);
        }
    }

    private static String paraHuesped(TipoNotificacion tipo, String detalle) {
        return switch (tipo) {
            case RESERVA_CONFIRMADA -> "Tu reserva " + detalle + " quedó confirmada.";
            case RESERVA_RECHAZADA -> "Tu reserva " + detalle + " fue rechazada porque el pago no fue aprobado.";
            case RESERVA_CANCELADA -> "Tu reserva " + detalle + " fue cancelada.";
            default -> throw new IllegalArgumentException("No es un aviso de reserva: " + tipo);
        };
    }

    private static String paraAnfitrion(TipoNotificacion tipo, String detalle) {
        return switch (tipo) {
            case RESERVA_CONFIRMADA -> "Se confirmó una reserva " + detalle + ".";
            case RESERVA_RECHAZADA -> "Se rechazó una reserva " + detalle + " porque el pago no fue aprobado.";
            case RESERVA_CANCELADA -> "Se canceló una reserva " + detalle + ".";
            default -> throw new IllegalArgumentException("No es un aviso de reserva: " + tipo);
        };
    }
}
