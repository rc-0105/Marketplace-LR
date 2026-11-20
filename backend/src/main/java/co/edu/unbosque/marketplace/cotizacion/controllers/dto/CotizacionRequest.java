package co.edu.unbosque.marketplace.cotizacion.controllers.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

public record CotizacionRequest(
        @NotNull(message = "Indica la publicación que quieres cotizar")
        Long publicacionId,

        @NotNull(message = "Indica la fecha de llegada")
        LocalDate llegada,

        @NotNull(message = "Indica la fecha de salida")
        LocalDate salida,

        Set<Long> serviciosAdicionales) {
}
