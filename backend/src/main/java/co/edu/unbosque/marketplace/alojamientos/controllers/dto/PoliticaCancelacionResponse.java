package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;

/**
 * Política de cancelación del catálogo: cancelar con al menos {@code diasSinCosto}
 * días de anticipación devuelve el 100 %; después, {@code porcentajeDevolucion}.
 */
public record PoliticaCancelacionResponse(
        Long id, String nombre, String descripcion, int diasSinCosto, int porcentajeDevolucion) {

    public static PoliticaCancelacionResponse desde(PoliticaCancelacion politica) {
        return new PoliticaCancelacionResponse(politica.getId(), politica.getNombre(), politica.getDescripcion(),
                politica.getDiasSinCosto(), politica.getPorcentajeDevolucion());
    }
}
