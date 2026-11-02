package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo con que el administrador devuelve una publicación; el anfitrión lo ve tal cual. */
public record DevolverPublicacionRequest(
        @NotBlank(message = "Indica las observaciones para el anfitrión.")
        @Size(max = 1000, message = "Las observaciones admiten máximo 1000 caracteres.")
        String observaciones) {
}
