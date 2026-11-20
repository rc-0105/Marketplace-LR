package co.edu.unbosque.marketplace.disponibilidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.disponibilidad.controllers.dto.ConsultaDisponibilidadRequest;
import co.edu.unbosque.marketplace.disponibilidad.controllers.dto.DisponibilidadRangoResponse;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta si una estancia está disponible (RF-26, RF-27): la responde con la misma regla que usa la
 * búsqueda. Es solo lectura e informativa; reservar vuelve a validar la disponibilidad.
 */
@RestController
@RequestMapping("/api/v1")
public class ConsultaDisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    public ConsultaDisponibilidadController(DisponibilidadService disponibilidadService) {
        this.disponibilidadService = disponibilidadService;
    }

    /** Disponibilidad de la estancia {@code [desde, hasta)} y, si no la hay, el motivo. */
    @GetMapping("/publicaciones/{id}/disponibilidad/consulta")
    @PreAuthorize(Permisos.HUESPED_O_ANFITRION)
    public DisponibilidadRangoResponse consultar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid ConsultaDisponibilidadRequest rango) {
        return DisponibilidadRangoResponse.desde(
                disponibilidadService.consultar(id, Long.valueOf(jwt.getSubject()), rango.desde(), rango.hasta()));
    }
}
