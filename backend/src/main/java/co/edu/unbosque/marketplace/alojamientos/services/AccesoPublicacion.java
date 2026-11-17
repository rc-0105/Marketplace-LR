package co.edu.unbosque.marketplace.alojamientos.services;

/**
 * Lo que otro módulo necesita saber para decidir quién ve una publicación, sin conocer el modelo de
 * {@code alojamientos}: su dueño y si es pública (está ACTIVA).
 */
public record AccesoPublicacion(Long anfitrionId, boolean publica) {

    public boolean esDe(Long usuarioId) {
        return anfitrionId.equals(usuarioId);
    }
}
