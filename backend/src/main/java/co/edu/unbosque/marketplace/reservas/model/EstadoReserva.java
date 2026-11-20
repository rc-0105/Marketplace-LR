package co.edu.unbosque.marketplace.reservas.model;

/**
 * Estado de una reserva (SOF-33, ADR-4). Nace PENDIENTE y el pago, que es aparte, la confirma;
 * CANCELADA libera las noches que ocupaba. PENDIENTE y CONFIRMADA son las que ocupan noches.
 */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, CANCELADA
}
