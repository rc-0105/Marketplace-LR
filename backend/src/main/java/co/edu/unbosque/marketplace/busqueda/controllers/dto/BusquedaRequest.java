package co.edu.unbosque.marketplace.busqueda.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;


public record BusquedaRequest(
        @NotBlank(message = "Indica la ciudad que quieres buscar")
        String ciudad,

        @NotNull(message = "Indica la fecha de llegada")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate llegada,

        @NotNull(message = "Indica la fecha de salida")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate salida) {
}
