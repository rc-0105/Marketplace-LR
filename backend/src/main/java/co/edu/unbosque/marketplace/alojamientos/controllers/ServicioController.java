package co.edu.unbosque.marketplace.alojamientos.controllers;

import co.edu.unbosque.marketplace.alojamientos.controllers.dto.ServicioResponse;
import co.edu.unbosque.marketplace.alojamientos.services.ServicioService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de servicios: de aquí salen los ids que se envían en una publicación. */
@RestController
@RequestMapping("/api/v1/servicios")
public class ServicioController {

    private final ServicioService servicioService;

    public ServicioController(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    @GetMapping
    @PreAuthorize(Permisos.AUTENTICADO)
    public List<ServicioResponse> listar() {
        return servicioService.listar().stream().map(ServicioResponse::desde).toList();
    }
}
