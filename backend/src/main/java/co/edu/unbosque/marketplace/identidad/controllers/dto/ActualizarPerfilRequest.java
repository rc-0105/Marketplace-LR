package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos editables del perfil (RF-08). El correo se declara solo para rechazarlo: si se ignorara, el
 * cliente creería que lo cambió.
 */
public record ActualizarPerfilRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres.")
        String nombre,

        @Pattern(regexp = "^\\s*$|^\\s*\\+?\\d{7,15}\\s*$",
                message = "El teléfono debe tener entre 7 y 15 dígitos y puede empezar con +.")
        String telefono,

        @Size(max = 500, message = "La foto admite máximo 500 caracteres.")
        @Pattern(regexp = "^\\s*$|^\\s*https?://\\S+\\s*$",
                message = "La foto debe ser una dirección que empiece con http:// o https://.")
        String fotoUrl,

        @Null(message = "El correo no se puede modificar.")
        String correo) {
}
