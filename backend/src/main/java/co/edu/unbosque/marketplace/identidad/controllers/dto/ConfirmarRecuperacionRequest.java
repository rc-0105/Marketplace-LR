package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Código recibido y contraseña nueva, que sigue la misma {@link ReglaContrasena} del registro. */
public record ConfirmarRecuperacionRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres.")
        String correo,

        @NotBlank(message = "El código es obligatorio.")
        @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos.")
        String codigo,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = ReglaContrasena.MINIMO, max = ReglaContrasena.MAXIMO, message = ReglaContrasena.LONGITUD)
        @Pattern(regexp = ReglaContrasena.PATRON, message = ReglaContrasena.REQUISITOS)
        String contrasenaNueva) {

    @Override
    public String toString() {
        // Ni el código ni la contraseña deben terminar en un log (RF-45).
        return "ConfirmarRecuperacionRequest[]";
    }
}
