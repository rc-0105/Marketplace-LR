package co.edu.unbosque.marketplace.compartido.consultas;

/**
 * Puerto con el que {@code alojamientos} pide, al bloquear una publicación (SOF-83, AC2), que sus reservas
 * futuras confirmadas pasen a revisión manual, sin conocer el modelo de reservas (ADR-2).
 *
 * <p>"Futuras" son las CONFIRMADA cuya salida es posterior a hoy en Colombia, la misma regla de la
 * cancelación. Se ejecuta dentro de la transacción del bloqueo: si el bloqueo falla, las reservas quedan
 * como estaban.
 *
 * <p>La implementación real es un {@code @Service} escaneado de {@code reservas}; mientras no exista,
 * {@code ConfiguracionReservasPorDefecto} registra una que no mueve nada.
 */
public interface RevisionReservasPublicacion {

    /** Pasa a revisión manual las reservas futuras confirmadas de la publicación y devuelve cuántas fueron. */
    int enviarARevision(Long publicacionId);
}
