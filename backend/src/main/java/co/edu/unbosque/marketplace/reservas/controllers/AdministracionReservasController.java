package co.edu.unbosque.marketplace.reservas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ReservaEnRevisionResponse;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ResolucionRevisionResponse;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ResolverRevisionRequest;
import co.edu.unbosque.marketplace.reservas.services.AdministracionReservasService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Revisión manual de las reservas de publicaciones bloqueadas (SOF-83, HU-38, RF-57): solo un administrador
 * las ve y decide si cada una se confirma o se cancela con devolución.
 */
@RestController
@RequestMapping("/api/v1/admin/reservas")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class AdministracionReservasController {

    private final AdministracionReservasService administracion;

    public AdministracionReservasController(AdministracionReservasService administracion) {
        this.administracion = administracion;
    }

    /** Las reservas en revisión manual, por fecha de llegada. */
    @GetMapping("/revision")
    public List<ReservaEnRevisionResponse> listarEnRevision() {
        return administracion.listarEnRevision().stream().map(ReservaEnRevisionResponse::desde).toList();
    }

    /** Confirma la reserva, o la cancela devolviendo el cobro en la pasarela. */
    @PostMapping("/{id}/resolver")
    public ResolucionRevisionResponse resolver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ResolverRevisionRequest data) {
        return ResolucionRevisionResponse.desde(
                administracion.resolver(id, Long.valueOf(jwt.getSubject()), data.decision(), data.motivo()));
    }
}
