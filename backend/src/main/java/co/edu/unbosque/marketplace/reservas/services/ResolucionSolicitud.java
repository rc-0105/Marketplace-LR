package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import java.time.Instant;

/**
 * Resultado de la respuesta del anfitrión a una solicitud (SOF-79): el estado en que quedó, el plazo de pago del
 * huésped si la aprobó ({@code venceEn}, {@code null} si la rechazó) y el motivo si la rechazó.
 */
public record ResolucionSolicitud(Long reservaId, EstadoReserva estado, Instant venceEn, String motivo) {
}
