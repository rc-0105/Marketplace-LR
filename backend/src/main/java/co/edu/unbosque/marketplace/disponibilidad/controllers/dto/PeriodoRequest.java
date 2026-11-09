package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Fechas en formato ISO (2027-01-15); la final es el día de salida y no cuenta como noche. */
public record PeriodoRequest(
        @NotNull(message = "La fecha inicial es obligatoria.") LocalDate fechaInicio,
        @NotNull(message = "La fecha final es obligatoria.") LocalDate fechaFin) {
}
