package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Estancia a consultar en fechas ISO ({@code 2027-01-15}), semiabierta {@code [desde, hasta)}. */
public record ConsultaDisponibilidadRequest(
        @NotNull(message = "Indica la fecha inicial") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @NotNull(message = "Indica la fecha final") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
}
