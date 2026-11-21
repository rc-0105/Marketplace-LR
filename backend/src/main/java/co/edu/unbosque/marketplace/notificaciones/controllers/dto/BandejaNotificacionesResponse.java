package co.edu.unbosque.marketplace.notificaciones.controllers.dto;

import co.edu.unbosque.marketplace.notificaciones.services.BandejaNotificaciones;
import java.util.List;

/** El contador de no leídas y las notificaciones más recientes del usuario. */
public record BandejaNotificacionesResponse(long noLeidas, List<NotificacionResponse> notificaciones) {

    public static BandejaNotificacionesResponse desde(BandejaNotificaciones bandeja) {
        return new BandejaNotificacionesResponse(bandeja.noLeidas(),
                bandeja.notificaciones().stream().map(NotificacionResponse::desde).toList());
    }
}
