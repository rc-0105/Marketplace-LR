package co.edu.unbosque.marketplace.resenas.model;

/**
 * Visibilidad de una reseña (SOF-80). Nace VISIBLE; la moderación (SOF-82) la pasa a OCULTA sin borrarla,
 * y una reseña OCULTA deja de mostrarse y de contar en el promedio.
 */
public enum EstadoResena {
    VISIBLE, OCULTA
}
