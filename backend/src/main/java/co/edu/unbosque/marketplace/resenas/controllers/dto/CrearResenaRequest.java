package co.edu.unbosque.marketplace.resenas.controllers.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Calificación de 1 a 5 y comentario con que un huésped reseña su estancia (SOF-80, HU-35, RF-53). */
public record CrearResenaRequest(
        @NotNull(message = "Indica la calificación")
        @Min(value = 1, message = "La calificación va de 1 a 5")
        @Max(value = 5, message = "La calificación va de 1 a 5")
        Integer calificacion,
        @NotBlank(message = "Escribe un comentario")
        @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
        String contenido) {
}
