package co.edu.unbosque.marketplace.cotizacion.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.cotizacion.controllers.dto.CotizacionRequest;
import co.edu.unbosque.marketplace.cotizacion.controllers.dto.CotizacionResponse;
import co.edu.unbosque.marketplace.cotizacion.services.CotizacionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cotización de una estancia para el huésped (RF-32, RF-33). */
@RestController
@RequestMapping("/api/v1/cotizaciones")
@PreAuthorize(Permisos.HUESPED)
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    /** Calcula el desglose sin guardar nada: una publicación inexistente o inactiva responde 404. */
    @PostMapping
    public CotizacionResponse cotizar(@Valid @RequestBody CotizacionRequest data) {
        return CotizacionResponse.desde(cotizacionService.cotizar(data.publicacionId(), data.llegada(),
                data.salida(), data.serviciosAdicionales()));
    }
}
