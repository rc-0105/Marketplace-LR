package co.edu.unbosque.marketplace.reservas.controllers.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado definitivo de la cancelación y estado independiente del reembolso.
 */
public record CancelacionResponse(Long reservaId, String estado, String motivo, BigDecimal valorDevolucion,
        Instant fecha, String resultadoReembolso, String reembolsoId) {
}
