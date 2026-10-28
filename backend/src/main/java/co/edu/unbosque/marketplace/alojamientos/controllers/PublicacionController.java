package co.edu.unbosque.marketplace.alojamientos.controllers;

import co.edu.unbosque.marketplace.alojamientos.controllers.dto.CrearPublicacionRequest;
import co.edu.unbosque.marketplace.alojamientos.controllers.dto.PublicacionResponse;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Publicaciones de alojamiento: solo un anfitrión crea las suyas (RF-11). */
@RestController
@RequestMapping("/api/v1/publicaciones")
public class PublicacionController {

	private final PublicacionService publicacionService;

	public PublicacionController(PublicacionService publicacionService) {
		this.publicacionService = publicacionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(Permisos.ANFITRION)
	public PublicacionResponse crear(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CrearPublicacionRequest data) {
		// El dueño sale del token, nunca del cuerpo: nadie publica a nombre de otro.
		Long anfitrionId = Long.valueOf(jwt.getSubject());
		Publicacion creada = publicacionService.crearBorrador(anfitrionId, data.aSolicitud());
		return PublicacionResponse.desde(creada);
	}
}
