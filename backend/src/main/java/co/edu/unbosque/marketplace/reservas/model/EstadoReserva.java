package co.edu.unbosque.marketplace.reservas.model;

/**
 * Estado de una reserva (SOF-33, ADR-4). Nace PENDIENTE y aparta las noches solo mientras dure su
 * apartado ({@code venceEn}); el pago, que es aparte, la confirma. CANCELADA libera las noches que
 * ocupaba, y VENCIDA es el estado terminal de una PENDIENTE cuyo apartado se agotó sin confirmarse.
 * Ocupan noches las CONFIRMADA y las PENDIENTE con el apartado vigente.
 */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, CANCELADA, VENCIDA
}
