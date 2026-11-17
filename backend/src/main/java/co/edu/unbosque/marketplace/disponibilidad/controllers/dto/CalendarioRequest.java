package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Ventana del calendario en fechas ISO ({@code 2027-01-15}), semiabierta {@code [desde, hasta)}. Los dos
 * extremos son opcionales: el que falta toma su valor por defecto.
 */
public record CalendarioRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
}
