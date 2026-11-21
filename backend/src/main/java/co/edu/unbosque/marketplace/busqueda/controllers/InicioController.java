package co.edu.unbosque.marketplace.busqueda.controllers;

import co.edu.unbosque.marketplace.busqueda.controllers.dto.ResultadoBusquedaResponse;
import co.edu.unbosque.marketplace.busqueda.services.BusquedaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vitrina pública del Inicio: la ruta es pública a propósito (ConfiguracionSeguridad) y por eso no declara
 * rol. Solo expone lo que ya ve un huésped en la búsqueda.
 */
@RestController
@RequestMapping("/api/v1/inicio")
public class InicioController {

    private static final int LIMITE_POR_DEFECTO = 12;

    private final BusquedaService busquedaService;

    public InicioController(BusquedaService busquedaService) {
        this.busquedaService = busquedaService;
    }

    /** Las publicaciones ACTIVAS más recientes; sin ninguna, una lista vacía. */
    @GetMapping("/destacados")
    public List<ResultadoBusquedaResponse> destacados(@RequestParam(defaultValue = "12") int limite) {
        return ResultadoBusquedaResponse.desde(busquedaService.destacados(limite));
    }
}
