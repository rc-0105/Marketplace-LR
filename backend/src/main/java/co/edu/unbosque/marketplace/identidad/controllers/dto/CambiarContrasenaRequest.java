package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cambio de la contraseña propia (RF-08). La nueva sigue {@link ReglaContrasena}. */
public record CambiarContrasenaRequest(
        @NotBlank(message = "La contraseña actual es obligatoria.")
        String contrasenaActual,

        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = ReglaContrasena.MINIMO, max = ReglaContrasena.MAXIMO, message = ReglaContrasena.LONGITUD)
        @Pattern(regexp = ReglaContrasena.PATRON, message = ReglaContrasena.REQUISITOS)
        String contrasenaNueva) {

    @Override
    public String toString() {
        // Ninguna de las dos contraseñas debe terminar en un log (RF-45).
        return "CambiarContrasenaRequest[]";
    }
}
