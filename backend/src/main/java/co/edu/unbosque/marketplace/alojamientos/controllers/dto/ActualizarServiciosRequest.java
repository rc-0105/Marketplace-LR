package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Servicios de la publicación como ids del catálogo; una lista vacía quita todos. */
public record ActualizarServiciosRequest(
        @NotNull(message = "La lista de servicios es obligatoria.")
        @Size(max = 30, message = "Se admiten máximo 30 servicios.")
        List<@NotNull(message = "Un servicio no puede estar vacío.") Long> servicios) {
}
