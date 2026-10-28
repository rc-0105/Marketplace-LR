package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.SolicitudBorrador;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/**
 * Datos del borrador. Solo el título es obligatorio; si llegan la capacidad o
 * el precio, deben ser positivos (RF-12).
 */
public record CrearPublicacionRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 120, message = "El título admite máximo 120 caracteres.")
        String titulo,

        @Size(max = 2000, message = "La descripción admite máximo 2000 caracteres.")
        String descripcion,

        @Size(max = 100, message = "La ciudad admite máximo 100 caracteres.")
        String ciudad,

        @Size(max = 200, message = "La dirección admite máximo 200 caracteres.")
        String direccion,

        String tipo,

        @Positive(message = "La capacidad debe ser mayor que cero.")
        Integer capacidad,

        @Positive(message = "El precio por noche debe ser mayor que cero.")
        @Digits(integer = 10, fraction = 2, message = "El precio por noche admite máximo 10 enteros y 2 decimales.")
        BigDecimal precioNoche,

        @Size(max = 30, message = "Se admiten máximo 30 servicios.")
        List<@NotBlank(message = "Un servicio no puede estar vacío.")
                @Size(max = 60, message = "Cada servicio admite máximo 60 caracteres.") String> servicios,

        @Size(max = 2000, message = "Las reglas admiten máximo 2000 caracteres.")
        String reglas,

        @Size(max = 10, message = "Se admiten máximo 10 imágenes.")
        List<@NotBlank(message = "La URL de la imagen no puede estar vacía.")
                @Size(max = 500, message = "Cada URL admite máximo 500 caracteres.")
                @URL(regexp = "^https?://.*", message = "Cada imagen debe ser una URL http o https.") String> imagenes) {

    public SolicitudBorrador aSolicitud() {
        return new SolicitudBorrador(titulo, descripcion, ciudad, direccion, tipo, capacidad, precioNoche,
                servicios, reglas, imagenes);
    }
}
