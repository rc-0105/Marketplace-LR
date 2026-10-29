package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;

/** Tipo de alojamiento del catálogo: el id es lo que se envía al crear o editar una publicación. */
public record TipoAlojamientoResponse(Long id, String nombre) {

    public static TipoAlojamientoResponse desde(TipoAlojamiento tipo) {
        return new TipoAlojamientoResponse(tipo.getId(), tipo.getNombre());
    }
}
