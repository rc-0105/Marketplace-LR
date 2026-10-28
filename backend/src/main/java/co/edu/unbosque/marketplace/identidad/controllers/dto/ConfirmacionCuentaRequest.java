package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirmación con la contraseña para dar de baja la propia cuenta; solo se valida la forma. */
public record ConfirmacionCuentaRequest(
        @NotBlank(message = "La contraseña es obligatoria.")
        String contrasena) {

    @Override
    public String toString() {
        // La contraseña no debe terminar en un log (RF-45).
        return "ConfirmacionCuentaRequest[]";
    }
}
