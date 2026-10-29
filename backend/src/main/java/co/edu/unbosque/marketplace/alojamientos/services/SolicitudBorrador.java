package co.edu.unbosque.marketplace.alojamientos.services;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos crudos del borrador tal como llegan al servicio. El tipo y los
 * servicios llegan como ids del catálogo; el servicio decide si existen.
 */
public record SolicitudBorrador(
        String titulo,
        String descripcion,
        String ciudad,
        String direccion,
        Long tipo,
        Integer capacidad,
        BigDecimal precioNoche,
        List<Long> servicios,
        String reglas,
        List<String> imagenes) {
}
