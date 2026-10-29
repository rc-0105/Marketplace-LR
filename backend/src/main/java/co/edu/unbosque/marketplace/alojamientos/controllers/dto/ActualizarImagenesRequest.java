package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/** Imágenes de la publicación en orden: la primera es la principal; una lista vacía quita todas. */
public record ActualizarImagenesRequest(
        @NotNull(message = "La lista de imágenes es obligatoria.")
        @Size(max = 10, message = "Se admiten máximo 10 imágenes.")
        List<@NotBlank(message = "La URL de la imagen no puede estar vacía.")
                @Size(max = 500, message = "Cada URL admite máximo 500 caracteres.")
                @URL(regexp = "^https?://.*", message = "Cada imagen debe ser una URL http o https.") String> imagenes) {
}
