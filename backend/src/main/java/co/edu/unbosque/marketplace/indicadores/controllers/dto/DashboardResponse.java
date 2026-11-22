package co.edu.unbosque.marketplace.indicadores.controllers.dto;

import co.edu.unbosque.marketplace.indicadores.services.CiudadDemanda;
import co.edu.unbosque.marketplace.indicadores.services.Dashboard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Indicadores del dashboard. Los mapas traen siempre todos los roles y todos los estados, en el
 * orden de su enum, aunque el conteo sea 0.
 */
public record DashboardResponse(
        LocalDate desde,
        LocalDate hasta,
        Map<String, Long> usuariosPorRol,
        long publicacionesActivas,
        Map<String, Long> reservasPorEstado,
        BigDecimal ingresosEstimados,
        List<CiudadDemanda> ciudadesMayorDemanda) {

    public static DashboardResponse desde(Dashboard dashboard) {
        return new DashboardResponse(
                dashboard.rango().desde(),
                dashboard.rango().hasta(),
                dashboard.usuariosPorRol(),
                dashboard.publicacionesActivas(),
                dashboard.reservasPorEstado(),
                dashboard.ingresosEstimados(),
                dashboard.ciudadesMayorDemanda());
    }
}
