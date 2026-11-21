package co.edu.unbosque.marketplace.resenas.controllers.dto;

import co.edu.unbosque.marketplace.resenas.model.EstadoResena;
import co.edu.unbosque.marketplace.resenas.model.Resena;

/** La reseña ya oculta, con el motivo registrado (SOF-82, HU-37). */
public record ResenaOcultadaResponse(Long id, EstadoResena estado, String motivo) {

    public static ResenaOcultadaResponse desde(Resena resena) {
        return new ResenaOcultadaResponse(resena.getId(), resena.getEstado(), resena.getMotivoOcultacion());
    }
}
