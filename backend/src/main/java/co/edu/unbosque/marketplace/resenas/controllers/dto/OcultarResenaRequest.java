package co.edu.unbosque.marketplace.resenas.controllers.dto;

import co.edu.unbosque.marketplace.resenas.model.Resena;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo con que el administrador oculta una reseña (SOF-82, HU-37). */
public record OcultarResenaRequest(
        @NotBlank(message = "Indica el motivo para ocultar la reseña.")
        @Size(max = Resena.MOTIVO_MAXIMO, message = "El motivo admite máximo 500 caracteres.")
        String motivo) {
}
