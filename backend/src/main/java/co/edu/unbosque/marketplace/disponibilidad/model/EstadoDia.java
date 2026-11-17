package co.edu.unbosque.marketplace.disponibilidad.model;

/**
 * Estado de un día en el calendario de una publicación (RF-25). Cuando varios aplican gana el primero:
 * RESERVADO, luego DISPONIBLE y, si ninguno, BLOQUEADO.
 */
public enum EstadoDia {
    /** Una reserva confirmada ocupa el día. */
    RESERVADO,
    /** Un periodo del anfitrión lo cubre y ninguna reserva lo ocupa. */
    DISPONIBLE,
    /**
     * Ningún periodo lo cubre. Es un estado derivado, no una marca guardada: hasta que exista un bloqueo
     * manual, "bloqueado" significa "fuera de cualquier periodo".
     */
    BLOQUEADO
}
