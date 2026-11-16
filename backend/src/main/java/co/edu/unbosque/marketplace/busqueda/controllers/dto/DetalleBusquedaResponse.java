package co.edu.unbosque.marketplace.busqueda.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.DetalleAlojamiento;
import co.edu.unbosque.marketplace.busqueda.services.DetalleBusqueda;
import co.edu.unbosque.marketplace.disponibilidad.services.PeriodoLibre;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Detalle de un alojamiento para el huésped. {@code calificacionPromedio} es nulo mientras
 * la publicación no tenga calificaciones: quien consume debe indicarlo, no mostrar cero.
 */
public record DetalleBusquedaResponse(Long id, String titulo, String descripcion, String ciudad, String tipo,
        Integer capacidad, BigDecimal precioNoche, String reglas, List<String> imagenes, List<String> servicios,
        String politicaCancelacion, String descripcionPolitica, List<PeriodoResponse> periodos,
        Double calificacionPromedio) {

    public record PeriodoResponse(LocalDate inicio, LocalDate fin) {
    }

    public static DetalleBusquedaResponse desde(DetalleBusqueda detalle) {
        DetalleAlojamiento alojamiento = detalle.alojamiento();
        List<PeriodoResponse> periodos = new ArrayList<>();
        for (PeriodoLibre periodo : detalle.periodos()) {
            periodos.add(new PeriodoResponse(periodo.inicio(), periodo.fin()));
        }
        return new DetalleBusquedaResponse(alojamiento.id(), alojamiento.titulo(), alojamiento.descripcion(),
                alojamiento.ciudad(), alojamiento.tipo(), alojamiento.capacidad(), alojamiento.precioNoche(),
                alojamiento.reglas(), alojamiento.imagenes(), alojamiento.servicios(),
                alojamiento.politicaCancelacion(), alojamiento.descripcionPolitica(), periodos, null);
    }
}
