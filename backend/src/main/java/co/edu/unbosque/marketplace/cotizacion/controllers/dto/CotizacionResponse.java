package co.edu.unbosque.marketplace.cotizacion.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.ServicioAdicionalDetalle;
import co.edu.unbosque.marketplace.cotizacion.services.Cotizacion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Desglose de la cotización: cada servicio adicional elegido es una línea aparte con su costo. */
public record CotizacionResponse(Long publicacionId, long noches, BigDecimal precioNoche, BigDecimal precioBase,
        BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento,
        List<LineaAdicionalResponse> serviciosAdicionales, BigDecimal totalServiciosAdicionales,
        BigDecimal total) {

    public record LineaAdicionalResponse(Long id, String nombre, BigDecimal costo) {
    }

    public static CotizacionResponse desde(Cotizacion cotizacion) {
        List<LineaAdicionalResponse> lineas = new ArrayList<>();
        for (ServicioAdicionalDetalle adicional : cotizacion.serviciosAdicionales()) {
            lineas.add(new LineaAdicionalResponse(adicional.id(), adicional.nombre(), adicional.costo()));
        }
        return new CotizacionResponse(cotizacion.publicacionId(), cotizacion.noches(), cotizacion.precioNoche(),
                cotizacion.precioBase(), cotizacion.tarifaLimpieza(), cotizacion.tarifaServicio(),
                cotizacion.descuento(), lineas, cotizacion.totalServiciosAdicionales(), cotizacion.total());
    }
}
