package co.edu.unbosque.marketplace.busqueda.services;

import java.math.BigDecimal;
import java.util.Set;

/** Filtros opcionales de la búsqueda: un campo nulo (o vacío) significa que ese filtro no se aplica. */
public record FiltrosBusqueda(Integer capacidad, String tipo, BigDecimal precioMin, BigDecimal precioMax,
        Set<Long> servicios) {

    public static FiltrosBusqueda ninguno() {
        return new FiltrosBusqueda(null, null, null, null, null);
    }
}
