package co.edu.unbosque.marketplace.resenas.controllers.dto;

import co.edu.unbosque.marketplace.resenas.model.Resena;
import java.time.Instant;

/** La reseña creada, asociada a su reserva, su publicación y su fecha (SOF-80, HU-35). */
public record ResenaResponse(Long id, Long reservaId, Long publicacionId, int calificacion, String contenido,
        Instant fecha) {

    public static ResenaResponse desde(Resena resena) {
        return new ResenaResponse(resena.getId(), resena.getReservaId(), resena.getPublicacionId(),
                resena.getCalificacion(), resena.getContenido(), resena.getFecha());
    }
}
