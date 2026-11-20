package co.edu.unbosque.marketplace.disponibilidad.model;

/**
 * Por qué una publicación no ofrece una estancia (RF-26, RF-27). Es derivado de los periodos y de las
 * reservas que ocupan noches, nunca una marca guardada. Cuando aplican los dos gana FUERA_DE_PERIODO.
 */
public enum MotivoNoDisponible {
    /** Ningún periodo único de la publicación contiene toda la estancia. */
    FUERA_DE_PERIODO,
    /** La estancia cabe en un periodo, pero una reserva que ocupa noches la cruza. */
    RESERVADO
}
