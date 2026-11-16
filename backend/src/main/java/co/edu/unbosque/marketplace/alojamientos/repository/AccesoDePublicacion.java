package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;

/** Dueño y estado de una publicación: decidir quién la ve no necesita cargar la entidad. */
public interface AccesoDePublicacion {

    Long getAnfitrionId();

    EstadoPublicacion getEstado();
}
