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
    private static final int MENSAJE_MAXIMO = 600;
    private static final int MOTIVO_MAXIMO = 300;
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

    /**
     * Avisa solo al anfitrión de la publicación (SOF-79: solicitud recibida). Igual que {@link #notificar}, nunca
     * lanza: el aviso es secundario a la operación que ya quedó guardada.
     */
    public void notificarAnfitrion(Reserva reserva, TipoNotificacion tipo) {
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
            publicaciones.consultarAcceso(publicacionId).map(AccesoPublicacion::anfitrionId)
                    .ifPresent(anfitrionId -> notificador.notificar(new NuevaNotificacion(anfitrionId, tipo,
                            paraAnfitrion(tipo, detalle), ENTIDAD, reserva.getId())));
        } catch (RuntimeException e) {
            LOG.error("No se pudo notificar al anfitrión la reserva {}: tipo={}", reserva.getId(), tipo, e);
        }
    }

    /**
     * Avisa solo al huésped de la reserva (SOF-79: solicitud aprobada, rechazada o vencida). {@code motivo} es el del
     * rechazo, o {@code null}. Nunca lanza.
     */
    public void notificarHuesped(Reserva reserva, TipoNotificacion tipo, String motivo) {
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
            notificador.notificar(new NuevaNotificacion(reserva.getHuespedId(), tipo,
                    recortar(paraHuespedSolicitud(tipo, detalle, motivo), MENSAJE_MAXIMO), ENTIDAD, reserva.getId()));
        } catch (RuntimeException e) {
            LOG.error("No se pudo notificar al huésped la reserva {}: tipo={}", reserva.getId(), tipo, e);
        }
    }

    private static String paraHuespedSolicitud(TipoNotificacion tipo, String detalle, String motivo) {
        return switch (tipo) {
            case SOLICITUD_APROBADA -> "Tu solicitud de reserva " + detalle
                    + " fue aprobada: ya puedes pagarla para confirmar la reserva.";
            case SOLICITUD_RECHAZADA -> "Tu solicitud de reserva " + detalle + " fue rechazada por el anfitrión."
                    + (motivo == null || motivo.isBlank() ? "" : " Motivo: " + recortar(motivo, MOTIVO_MAXIMO));
            case SOLICITUD_VENCIDA -> "Tu solicitud de reserva " + detalle
                    + " venció porque el anfitrión no respondió a tiempo.";
            default -> throw new IllegalArgumentException("No es un aviso de solicitud: " + tipo);
        };
    }

    // El mensaje de notificacion admite 600 caracteres (V28): el motivo se recorta antes y el total al final.
    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
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
            case SOLICITUD_RECIBIDA -> "Tienes una nueva solicitud de reserva " + detalle + ".";
            default -> throw new IllegalArgumentException("No es un aviso de reserva: " + tipo);
        };
    }
}
