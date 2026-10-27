package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Credenciales del login: solo se valida la forma, nunca la fortaleza de la contraseña. */
public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria.")
        String contrasena) {

    @Override
    public String toString() {
        // Ni el correo ni la contraseña deben terminar en un log (RF-45).
        return "LoginRequest[]";
    }
}
