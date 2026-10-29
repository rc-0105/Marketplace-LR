package co.edu.unbosque.marketplace.alojamientos.controllers;

import co.edu.unbosque.marketplace.alojamientos.controllers.dto.CrearPublicacionRequest;
import co.edu.unbosque.marketplace.alojamientos.controllers.dto.PublicacionResponse;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
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
 * Publicaciones de alojamiento: solo un anfitrión crea las suyas (RF-11) y las
 * consulta, edita o elimina; el administrador puede hacerlo con cualquiera (RF-13).
 */
@RestController
@RequestMapping("/api/v1/publicaciones")
public class PublicacionController {

	private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

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

	/** Las publicaciones del anfitrión autenticado, la más reciente primero. */
	@GetMapping("/mias")
	@PreAuthorize(Permisos.ANFITRION)
	public List<PublicacionResponse> listarMias(@AuthenticationPrincipal Jwt jwt) {
		return publicacionService.listarDelAnfitrion(usuarioDe(jwt)).stream()
				.map(PublicacionResponse::desde).toList();
	}

	/** Detalle para editarla: el PUT reemplaza la publicación completa, así que primero se cargan los datos actuales. */
	@GetMapping("/{id}")
	@PreAuthorize(Permisos.ANFITRION_O_ADMINISTRADOR)
	public PublicacionResponse consultar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Publicacion publicacion = publicacionService.consultar(id, usuarioDe(jwt), esAdministrador(jwt));
		return PublicacionResponse.desde(publicacion);
	}

	/**
	 * Reemplaza los datos de la publicación; recibe el mismo cuerpo que la creación
	 * (RF-13). Lo que no llegue queda vacío, incluidos los servicios y las imágenes.
	 */
	@PutMapping("/{id}")
	@PreAuthorize(Permisos.ANFITRION_O_ADMINISTRADOR)
	public PublicacionResponse editar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody CrearPublicacionRequest data) {
		Publicacion editada = publicacionService.editar(id, usuarioDe(jwt), esAdministrador(jwt),
				data.aSolicitud());
		return PublicacionResponse.desde(editada);
	}

	/** Elimina un borrador con sus imágenes y servicios. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(Permisos.ANFITRION_O_ADMINISTRADOR)
	public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		publicacionService.eliminar(id, usuarioDe(jwt), esAdministrador(jwt));
	}

	private static Long usuarioDe(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

	// El rol sale del token firmado; el service decide qué permite cada rol.
	private static boolean esAdministrador(Jwt jwt) {
		return ROL_ADMINISTRADOR.equals(jwt.getClaimAsString("rol"));
	}
}
