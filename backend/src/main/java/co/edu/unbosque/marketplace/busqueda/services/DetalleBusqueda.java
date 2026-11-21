package co.edu.unbosque.marketplace.busqueda.services;

import co.edu.unbosque.marketplace.alojamientos.services.DetalleAlojamiento;
import co.edu.unbosque.marketplace.disponibilidad.services.PeriodoLibre;
import java.util.List;

/**
 * El alojamiento junto con su calendario de disponibilidad y su calificación promedio, nula mientras no tenga
 * reseñas visibles (SOF-81).
 */
public record DetalleBusqueda(DetalleAlojamiento alojamiento, List<PeriodoLibre> periodos,
        Double calificacionPromedio) {
}
