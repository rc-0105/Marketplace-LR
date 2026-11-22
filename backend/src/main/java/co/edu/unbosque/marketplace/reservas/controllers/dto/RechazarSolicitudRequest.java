package co.edu.unbosque.marketplace.reservas.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo con que el anfitrión rechaza una solicitud de reserva (SOF-79, HU-34); obligatorio, máximo 500. */
public record RechazarSolicitudRequest(
        @NotBlank(message = "Indica el motivo del rechazo")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {
}
