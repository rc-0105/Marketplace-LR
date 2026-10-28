package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo que el administrador deja al restringir; el dueño de la cuenta lo ve al iniciar sesión. */
public record RestringirCuentaRequest(
        @NotBlank(message = "El motivo es obligatorio.")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
        String motivo) {
}
