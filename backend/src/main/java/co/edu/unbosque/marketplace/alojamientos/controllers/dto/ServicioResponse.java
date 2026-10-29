package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.model.Servicio;

/** Servicio del catálogo: el id es lo que se envía al crear o editar una publicación. */
public record ServicioResponse(Long id, String nombre) {

    public static ServicioResponse desde(Servicio servicio) {
        return new ServicioResponse(servicio.getId(), servicio.getNombre());
    }
}
