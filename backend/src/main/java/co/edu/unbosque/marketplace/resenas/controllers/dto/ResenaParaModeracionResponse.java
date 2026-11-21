package co.edu.unbosque.marketplace.resenas.controllers.dto;

import co.edu.unbosque.marketplace.resenas.services.ResenaParaModeracion;
import java.time.Instant;

/** Una reseña visible en el listado de moderación del administrador (SOF-82, HU-37). */
public record ResenaParaModeracionResponse(Long id, Long publicacionId, String autor, int calificacion,
        String contenido, Instant fecha) {

    public static ResenaParaModeracionResponse desde(ResenaParaModeracion resena) {
        return new ResenaParaModeracionResponse(resena.id(), resena.publicacionId(), resena.autor(),
                resena.calificacion(), resena.contenido(), resena.fecha());
    }
}
