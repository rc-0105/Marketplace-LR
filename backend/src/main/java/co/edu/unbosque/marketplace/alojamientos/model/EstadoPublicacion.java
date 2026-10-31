package co.edu.unbosque.marketplace.alojamientos.model;

/**
 * Estado de una publicación (RF-14, RF-16, RF-17). Solo una publicación ACTIVA
 * aparece en las búsquedas y recibe reservas.
 */
public enum EstadoPublicacion {
    /** Recién creada o devuelta por el administrador: solo la ve su dueño. */
    BORRADOR,
    /** El anfitrión pidió activarla y espera la aprobación de un administrador. */
    PENDIENTE_REVISION,
    ACTIVA,
    /** El anfitrión la sacó de las búsquedas; al reactivarla vuelve a ACTIVA. */
    PAUSADA
}
