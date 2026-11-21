package co.edu.unbosque.marketplace.reservas.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de la cancelación de una reserva, indicado por quien cancela (SOF-75, HU-30, RF-46). */
public record CancelarReservaRequest(
        @NotBlank(message = "Indica el motivo de la cancelación")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {
}
