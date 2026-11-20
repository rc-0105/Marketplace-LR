package co.edu.unbosque.marketplace.cotizacion.services;

import co.edu.unbosque.marketplace.alojamientos.services.ServicioAdicionalDetalle;
import java.math.BigDecimal;
import java.util.List;

/**
 * Desglose del precio de una estancia (RF-32, RF-33). El total es el precio base más la limpieza,
 * la tarifa de servicio y los servicios adicionales elegidos, menos el descuento.
 */
public record Cotizacion(Long publicacionId, long noches, BigDecimal precioNoche, BigDecimal precioBase,
        BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento,
        List<ServicioAdicionalDetalle> serviciosAdicionales, BigDecimal totalServiciosAdicionales,
        BigDecimal total) {
}
