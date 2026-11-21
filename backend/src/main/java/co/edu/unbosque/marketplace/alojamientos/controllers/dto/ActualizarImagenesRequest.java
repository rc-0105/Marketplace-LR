package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Imágenes de la publicación en orden: la primera es la principal; una lista vacía quita todas. Cada una es
 * una URL http o https, o la ruta de una imagen subida a la plataforma.
 */
public record ActualizarImagenesRequest(
        @NotNull(message = "La lista de imágenes es obligatoria.")
        @Size(max = 10, message = "Se admiten máximo 10 imágenes.")
        List<@NotBlank(message = "La URL de la imagen no puede estar vacía.")
                @Size(max = 500, message = "Cada URL admite máximo 500 caracteres.")
                @UrlDeImagen String> imagenes) {
}
