package co.edu.unbosque.marketplace.disponibilidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.disponibilidad.controllers.dto.PeriodoRequest;
import co.edu.unbosque.marketplace.disponibilidad.controllers.dto.PeriodoResponse;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Periodos disponibles de una publicación (RF-23, RF-24): solo el anfitrión dueño
 * los registra, consulta, modifica y elimina.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.ANFITRION)
public class DisponibilidadController {

	private final DisponibilidadService disponibilidadService;

	public DisponibilidadController(DisponibilidadService disponibilidadService) {
		this.disponibilidadService = disponibilidadService;
	}

	@PostMapping("/publicaciones/{id}/disponibilidad")
	@ResponseStatus(HttpStatus.CREATED)
	public PeriodoResponse registrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody PeriodoRequest data) {
		return PeriodoResponse.desde(
				disponibilidadService.registrar(id, usuarioDe(jwt), data.fechaInicio(), data.fechaFin()));
	}

	/** Los periodos de la publicación, del más próximo al más lejano. */
	@GetMapping("/publicaciones/{id}/disponibilidad")
	public List<PeriodoResponse> listar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return disponibilidadService.listar(id, usuarioDe(jwt)).stream().map(PeriodoResponse::desde).toList();
	}

	@PutMapping("/disponibilidad/{periodoId}")
	public PeriodoResponse modificar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long periodoId,
			@Valid @RequestBody PeriodoRequest data) {
		return PeriodoResponse.desde(
				disponibilidadService.modificar(periodoId, usuarioDe(jwt), data.fechaInicio(), data.fechaFin()));
	}

	@DeleteMapping("/disponibilidad/{periodoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long periodoId) {
		disponibilidadService.eliminar(periodoId, usuarioDe(jwt));
	}

	private static Long usuarioDe(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
