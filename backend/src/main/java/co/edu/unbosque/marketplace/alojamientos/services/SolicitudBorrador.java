package co.edu.unbosque.marketplace.alojamientos.services;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos crudos del borrador tal como llegan al servicio. El tipo viaja como
 * texto para que el servicio decida si es válido, igual que el rol en el registro;
 * los servicios llegan como ids del catálogo.
 */
public record SolicitudBorrador(
        String titulo,
        String descripcion,
        String ciudad,
        String direccion,
        String tipo,
        Integer capacidad,
        BigDecimal precioNoche,
        List<Long> servicios,
        String reglas,
        List<String> imagenes) {
}
