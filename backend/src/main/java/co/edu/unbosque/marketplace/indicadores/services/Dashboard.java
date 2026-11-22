package co.edu.unbosque.marketplace.indicadores.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Indicadores clave del negocio calculados sobre un rango de fechas (RF-41). */
public record Dashboard(
        RangoFechas rango,
        Map<String, Long> usuariosPorRol,
        long publicacionesActivas,
        Map<String, Long> reservasPorEstado,
        BigDecimal ingresosEstimados,
        List<CiudadDemanda> ciudadesMayorDemanda) {
}
