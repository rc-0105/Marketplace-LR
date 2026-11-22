package co.edu.unbosque.marketplace.indicadores.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.indicadores.controllers.dto.ConsultaDashboardRequest;
import co.edu.unbosque.marketplace.indicadores.controllers.dto.DashboardResponse;
import co.edu.unbosque.marketplace.indicadores.services.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard de indicadores del negocio para el administrador (RF-41, RF-42, HU-27). */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Indicadores calculados sobre el rango de fechas; sin fechas, sobre todo el histórico. */
    @GetMapping
    public DashboardResponse consultar(ConsultaDashboardRequest peticion) {
        return DashboardResponse.desde(dashboardService.calcular(peticion.rango()));
    }
}
