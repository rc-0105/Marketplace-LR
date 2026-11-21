package co.edu.unbosque.marketplace.resenas.services;

import java.time.Instant;

/** Reseña visible tal como la ve el administrador al moderar, con su publicación y su autor (SOF-82). */
public record ResenaParaModeracion(Long id, Long publicacionId, String autor, int calificacion, String contenido,
        Instant fecha) {
}
