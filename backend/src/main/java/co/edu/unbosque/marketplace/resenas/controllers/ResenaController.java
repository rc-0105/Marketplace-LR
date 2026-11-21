package co.edu.unbosque.marketplace.resenas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.resenas.controllers.dto.CrearResenaRequest;
import co.edu.unbosque.marketplace.resenas.controllers.dto.ResenaResponse;
import co.edu.unbosque.marketplace.resenas.services.ResenaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reseña de una estancia por su huésped (SOF-80, HU-35). */
@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize(Permisos.HUESPED)
public class ResenaController {

    private final ResenaService resenaService;

    public ResenaController(ResenaService resenaService) {
        this.resenaService = resenaService;
    }

    /**
     * Reseña la reserva COMPLETADA del huésped autenticado. El huésped sale del token, nunca del cuerpo; que
     * la reserva sea suya lo decide el service.
     */
    @PostMapping("/{id}/resena")
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaResponse crear(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody CrearResenaRequest data) {
        return ResenaResponse.desde(resenaService.crear(id, Long.valueOf(jwt.getSubject()), data.calificacion(),
                data.contenido()));
    }
}
