package co.edu.unbosque.marketplace.alojamientos.controllers;

import co.edu.unbosque.marketplace.alojamientos.controllers.dto.PoliticaCancelacionResponse;
import co.edu.unbosque.marketplace.alojamientos.controllers.dto.ServicioResponse;
import co.edu.unbosque.marketplace.alojamientos.controllers.dto.TipoAlojamientoResponse;
import co.edu.unbosque.marketplace.alojamientos.services.CatalogoService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogos de la publicación (RF-18, RF-20): de aquí salen los ids que se envían al crearla o editarla. */
@RestController
@RequestMapping("/api/v1/catalogos")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/servicios")
    @PreAuthorize(Permisos.AUTENTICADO)
    public List<ServicioResponse> listarServicios() {
        return catalogoService.listarServicios().stream().map(ServicioResponse::desde).toList();
    }

    @GetMapping("/tipos-alojamiento")
    @PreAuthorize(Permisos.AUTENTICADO)
    public List<TipoAlojamientoResponse> listarTiposAlojamiento() {
        return catalogoService.listarTiposAlojamiento().stream().map(TipoAlojamientoResponse::desde).toList();
    }

    /** De la más flexible a la más estricta (RF-20). */
    @GetMapping("/politicas-cancelacion")
    @PreAuthorize(Permisos.AUTENTICADO)
    public List<PoliticaCancelacionResponse> listarPoliticasCancelacion() {
        return catalogoService.listarPoliticasCancelacion().stream().map(PoliticaCancelacionResponse::desde)
                .toList();
    }
}
