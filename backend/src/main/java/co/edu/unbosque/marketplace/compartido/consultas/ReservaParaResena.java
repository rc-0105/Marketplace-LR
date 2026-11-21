package co.edu.unbosque.marketplace.compartido.consultas;

/**
 * Lo que {@code resenas} necesita saber de una reserva para decidir si se puede reseñar (SOF-80): quién es su
 * huésped titular, a qué publicación pertenece y en qué estado está.
 */
public record ReservaParaResena(Long reservaId, Long huespedId, Long publicacionId, String estado) {

    private static final String COMPLETADA = "COMPLETADA";

    /** Solo una reserva COMPLETADA, con la estancia ya terminada, admite una reseña. */
    public boolean estaCompletada() {
        return COMPLETADA.equals(estado);
    }
}
