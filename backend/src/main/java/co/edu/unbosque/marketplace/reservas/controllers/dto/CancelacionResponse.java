package co.edu.unbosque.marketplace.reservas.controllers.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado de cancelar una reserva (SOF-75, RF-46/RF-48): el valor de devolución es el 100% del total de
 * la reserva, sin importar quién la canceló.
 */
public record CancelacionResponse(Long reservaId, String estado, String motivo, BigDecimal valorDevolucion,
        Instant fecha) {
}
