package co.edu.unbosque.marketplace.notificaciones.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.notificaciones.controllers.dto.BandejaNotificacionesResponse;
import co.edu.unbosque.marketplace.notificaciones.controllers.dto.NotificacionResponse;
import co.edu.unbosque.marketplace.notificaciones.services.NotificacionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Las notificaciones del usuario autenticado (SOF-37, RF-40, RF-41): nunca las de otro. */
@RestController
@RequestMapping("/api/v1/notificaciones")
@PreAuthorize(Permisos.AUTENTICADO)
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** Las 50 más recientes, de la más nueva a la más vieja, con el contador de no leídas. */
    @GetMapping
    public BandejaNotificacionesResponse listar(@AuthenticationPrincipal Jwt jwt) {
        return BandejaNotificacionesResponse.desde(notificaciones.listar(Long.valueOf(jwt.getSubject())));
    }

    /** Marca leída una notificación propia; una ajena o inexistente responde 404. */
    @PostMapping("/{id}/leer")
    public NotificacionResponse leer(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return NotificacionResponse.desde(notificaciones.marcarLeida(id, Long.valueOf(jwt.getSubject())));
    }
}
