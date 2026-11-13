package co.edu.unbosque.marketplace.busqueda.controllers;

import co.edu.unbosque.marketplace.busqueda.controllers.dto.BusquedaRequest;
import co.edu.unbosque.marketplace.busqueda.controllers.dto.ResultadoBusquedaResponse;
import co.edu.unbosque.marketplace.busqueda.services.BusquedaService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Búsqueda de alojamientos del huésped (RF-28, RF-30). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.HUESPED)
public class BusquedaController {

	private final BusquedaService busquedaService;

	public BusquedaController(BusquedaService busquedaService) {
		this.busquedaService = busquedaService;
	}

	/** Las ACTIVAS de la ciudad con disponibilidad en el rango; sin coincidencias, una lista vacía. */
	@GetMapping("/busqueda")
	public List<ResultadoBusquedaResponse> buscar(@Valid BusquedaRequest data) {
		return ResultadoBusquedaResponse
				.desde(busquedaService.buscar(data.ciudad(), data.llegada(), data.salida()));
	}
}
