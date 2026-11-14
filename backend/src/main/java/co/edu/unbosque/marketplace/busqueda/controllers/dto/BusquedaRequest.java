package co.edu.unbosque.marketplace.busqueda.controllers.dto;

import co.edu.unbosque.marketplace.busqueda.services.FiltrosBusqueda;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;


public record BusquedaRequest(
        @NotBlank(message = "Indica la ciudad que quieres buscar")
        String ciudad,

        @NotNull(message = "Indica la fecha de llegada")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate llegada,

        @NotNull(message = "Indica la fecha de salida")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate salida,

        @Positive(message = "La capacidad debe ser mayor que cero")
        Integer capacidad,

        String tipo,

        @PositiveOrZero(message = "El precio mínimo no puede ser negativo")
        BigDecimal precioMin,

        @PositiveOrZero(message = "El precio máximo no puede ser negativo")
        BigDecimal precioMax,

        Set<Long> servicios) {

    public FiltrosBusqueda filtros() {
        return new FiltrosBusqueda(capacidad, tipo, precioMin, precioMax, servicios);
    }
}
