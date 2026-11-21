package co.edu.unbosque.marketplace.notificaciones.controllers.dto;

import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.notificaciones.model.Notificacion;
import java.time.Instant;

/** Una notificación tal como la ve su destinatario, en la lista y al marcarla leída. */
public record NotificacionResponse(Long id, TipoNotificacion tipo, String mensaje, String entidad, Long entidadId,
        boolean leida, Instant creadaEn) {

    public static NotificacionResponse desde(Notificacion notificacion) {
        return new NotificacionResponse(notificacion.getId(), notificacion.getTipo(), notificacion.getMensaje(),
                notificacion.getEntidad(), notificacion.getEntidadId(), notificacion.isLeida(),
                notificacion.getCreadoEn());
    }
}
