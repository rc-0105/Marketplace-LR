package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;

/**
 * Fila del buscador de moderación (SOF-83): lo necesario para elegir qué publicación bloquear.
 * {@code motivoBloqueo} solo tiene valor si ya está BLOQUEADA.
 */
public record PublicacionModeracionResponse(Long id, String titulo, String ciudad, String estado, Long anfitrionId,
        String motivoBloqueo) {

    public static PublicacionModeracionResponse desde(Publicacion publicacion) {
        return new PublicacionModeracionResponse(publicacion.getId(), publicacion.getTitulo(), publicacion.getCiudad(),
                publicacion.getEstado().name(), publicacion.getAnfitrionId(), publicacion.getMotivoBloqueo());
    }
}
