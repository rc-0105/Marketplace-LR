package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo que el administrador deja al bloquear una publicación (SOF-83); el anfitrión lo ve. */
public record BloquearPublicacionRequest(
        @NotBlank(message = "El motivo es obligatorio.")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
        String motivo) {
}
