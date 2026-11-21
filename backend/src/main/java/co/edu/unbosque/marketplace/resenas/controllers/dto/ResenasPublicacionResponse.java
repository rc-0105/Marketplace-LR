package co.edu.unbosque.marketplace.resenas.controllers.dto;

import co.edu.unbosque.marketplace.resenas.services.ResenaPublicada;
import co.edu.unbosque.marketplace.resenas.services.ResenasPublicacion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Las reseñas visibles de una publicación, de la más reciente a la más antigua. {@code promedio} es nulo
 * mientras no haya ninguna: quien consume debe indicarlo, no mostrar cero (SOF-81, HU-36).
 */
public record ResenasPublicacionResponse(Double promedio, List<ResenaPublicadaResponse> resenas) {

    /** Una reseña con su autor, vinculada a la reserva que le dio origen. */
    public record ResenaPublicadaResponse(Long id, Long reservaId, String autor, int calificacion,
            String contenido, Instant fecha) {
    }

    public static ResenasPublicacionResponse desde(ResenasPublicacion publicacion) {
        List<ResenaPublicadaResponse> resenas = new ArrayList<>();
        for (ResenaPublicada resena : publicacion.resenas()) {
            resenas.add(new ResenaPublicadaResponse(resena.id(), resena.reservaId(), resena.autor(),
                    resena.calificacion(), resena.contenido(), resena.fecha()));
        }
        return new ResenasPublicacionResponse(publicacion.promedio(), resenas);
    }
}
