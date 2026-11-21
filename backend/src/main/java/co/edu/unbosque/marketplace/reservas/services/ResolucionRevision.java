package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import java.math.BigDecimal;

/**
 * Cómo quedó una reserva en revisión manual tras resolverla (SOF-83): CONFIRMADA, o CANCELADA con el valor
 * devuelto ({@code valorDevolucion} es nulo al confirmar). {@code reembolsado} indica si la pasarela devolvió
 * el cobro.
 */
public record ResolucionRevision(Long reservaId, EstadoReserva estado, boolean reembolsado,
        BigDecimal valorDevolucion) {
}
