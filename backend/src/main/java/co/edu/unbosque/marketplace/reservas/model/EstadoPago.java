package co.edu.unbosque.marketplace.reservas.model;

/**
 * Resultado del cobro de una reserva en la pasarela (SOF-34, RF-37). APROBADO acompaña a una reserva
 * CONFIRMADA y RECHAZADO a una CANCELADA; ERROR deja la reserva PENDIENTE hasta que se reintente el pago o
 * venza su apartado, y la conciliación lo resuelve: lo confirma, lo reembolsa (REEMBOLSADO, si la pasarela
 * cobró pero la reserva ya no estaba PENDIENTE) o lo cierra como NO_COBRADO si la pasarela nunca cobró.
 */
public enum EstadoPago {
    SIN_PAGO,
    APROBADO,
    RECHAZADO,
    ERROR,
    REEMBOLSADO,
    NO_COBRADO
}
