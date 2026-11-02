package co.edu.unbosque.marketplace.alojamientos.controllers;

import co.edu.unbosque.marketplace.alojamientos.controllers.dto.DevolverPublicacionRequest;
import co.edu.unbosque.marketplace.alojamientos.controllers.dto.PublicacionResponse;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
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
 * Revisión administrativa de publicaciones (RF-15): solo un administrador ve las
 * pendientes y decide si se publican o vuelven al anfitrión con observaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/publicaciones")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class RevisionPublicacionController {

	private final PublicacionService publicacionService;

	public RevisionPublicacionController(PublicacionService publicacionService) {
		this.publicacionService = publicacionService;
	}

	/** Las publicaciones que esperan revisión, la que lleva más tiempo esperando primero. */
	@GetMapping("/pendientes")
	public List<PublicacionResponse> listarPendientes() {
		return publicacionService.listarPendientes().stream().map(PublicacionResponse::desde).toList();
	}

	/** Publica una publicación pendiente: pasa a ACTIVA. */
	@PostMapping("/{id}/aprobar")
	public PublicacionResponse aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return PublicacionResponse.desde(publicacionService.aprobar(id, usuarioDe(jwt)));
	}

	/** Devuelve una publicación pendiente a BORRADOR con las observaciones para el anfitrión. */
	@PostMapping("/{id}/devolver")
	public PublicacionResponse devolver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody DevolverPublicacionRequest data) {
		return PublicacionResponse.desde(publicacionService.devolver(id, usuarioDe(jwt), data.observaciones()));
	}

	private static Long usuarioDe(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
