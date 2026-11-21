package co.edu.unbosque.marketplace.resenas.services;

import java.time.Instant;

/** Una reseña visible con su autor, vinculada a la reserva que le dio origen (SOF-81, HU-36). */
public record ResenaPublicada(Long id, Long reservaId, String autor, int calificacion, String contenido,
        Instant fecha) {
}
