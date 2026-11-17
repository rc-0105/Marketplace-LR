package co.edu.unbosque.marketplace.disponibilidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.disponibilidad.controllers.dto.CalendarioRequest;
import co.edu.unbosque.marketplace.disponibilidad.controllers.dto.CalendarioResponse;
import co.edu.unbosque.marketplace.disponibilidad.services.CalendarioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendario de disponibilidad de una publicación (RF-25): lo consultan huéspedes y anfitriones, y el
 * service decide qué ve cada uno.
 */
@RestController
@RequestMapping("/api/v1")
public class CalendarioController {

	private final CalendarioService calendarioService;

	public CalendarioController(CalendarioService calendarioService) {
		this.calendarioService = calendarioService;
	}

	/** Estado de cada día de la ventana {@code [desde, hasta)}; sin parámetros, los próximos 12 meses. */
	@GetMapping("/publicaciones/{id}/calendario")
	@PreAuthorize(Permisos.HUESPED_O_ANFITRION)
	public CalendarioResponse consultar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid CalendarioRequest rango) {
		return CalendarioResponse
				.desde(calendarioService.consultar(id, Long.valueOf(jwt.getSubject()), rango.desde(), rango.hasta()));
	}
}
