package co.edu.unbosque.marketplace.alojamientos.services;

import java.math.BigDecimal;
import java.util.List;

/**
 * Condiciones comerciales tal como llegan al servicio. La política es un id del
 * catálogo; el modo llega como texto y, si falta, se toma reserva inmediata.
 */
public record SolicitudConfiguracion(
        BigDecimal tarifaLimpieza,
        String modoReserva,
        Long politicaCancelacion,
        List<ServicioAdicional> serviciosAdicionales) {

    public record ServicioAdicional(String nombre, BigDecimal costo) {
    }
}
