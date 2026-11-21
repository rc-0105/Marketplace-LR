package co.edu.unbosque.marketplace.compartido.notificaciones;

/**
 * Una notificación por registrar, con el texto ya armado: el título y las fechas quedan congelados como
 * estaban al momento del aviso. {@code entidad} y {@code entidadId} señalan lo que la originó, por ejemplo
 * {@code "Reserva"} y el id de la reserva.
 */
public record NuevaNotificacion(Long destinatarioId, TipoNotificacion tipo, String mensaje, String entidad,
        Long entidadId) {
}
