package co.edu.unbosque.marketplace.reservas.model;

/**
 * Estado de una reserva (SOF-33, ADR-4). Nace PENDIENTE y aparta las noches solo mientras dure su
 * apartado ({@code venceEn}); el pago, que es aparte, la confirma. CANCELADA libera las noches que
 * ocupaba, y VENCIDA es el estado terminal de una PENDIENTE cuyo apartado se agotó sin confirmarse.
 * Ocupan noches las CONFIRMADA, las EN_REVISION_MANUAL y las PENDIENTE con el apartado vigente.
 * EN_REVISION_MANUAL es una CONFIRMADA cuya publicación fue bloqueada (SOF-83): espera a que un administrador
 * la confirme o la cancele con devolución.
 *
 * <p>PENDIENTE_APROBACION (SOF-79, RF-52) es una solicitud sobre una publicación con modo de reserva por aprobación:
 * aparta las noches hasta {@code venceEn} (la ventana de aprobación del anfitrión) como una PENDIENTE. Si el anfitrión
 * la aprueba pasa a PENDIENTE con un apartado nuevo para que el huésped pague; si la rechaza queda RECHAZADA, con
 * motivo, y si la ventana se agota pasa a VENCIDA. Ambas liberan las noches.
 *
 * <p>COMPLETADA (SOF-78, RF-50) es el estado terminal de una CONFIRMADA cuya fecha de salida ya
 * pasó: la tarea programada de {@link co.edu.unbosque.marketplace.reservas.services.CierreReservasService}
 * la cierra, lo que habilita la reseña y la saca de la restricción de exclusión, igual que CANCELADA
 * y VENCIDA.
 */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, CANCELADA, VENCIDA, COMPLETADA, EN_REVISION_MANUAL, PENDIENTE_APROBACION, RECHAZADA
}
