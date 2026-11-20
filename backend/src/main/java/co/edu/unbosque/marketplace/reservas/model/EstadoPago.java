package co.edu.unbosque.marketplace.reservas.model;

/**
 * Resultado del cobro de una reserva en la pasarela (SOF-34, RF-37). APROBADO acompaña a una reserva
 * CONFIRMADA y RECHAZADO a una CANCELADA; ERROR deja la reserva PENDIENTE hasta que se reintente el pago o
 * venza su apartado.
 */
public enum EstadoPago {
    SIN_PAGO,
    APROBADO,
    RECHAZADO,
    ERROR
}
