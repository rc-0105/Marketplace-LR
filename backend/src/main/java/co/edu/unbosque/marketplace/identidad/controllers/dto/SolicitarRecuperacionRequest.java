package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correo de la cuenta que quiere recuperar el acceso. */
public record SolicitarRecuperacionRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres.")
        String correo) {

    @Override
    public String toString() {
        return "SolicitarRecuperacionRequest[]";
    }
}
