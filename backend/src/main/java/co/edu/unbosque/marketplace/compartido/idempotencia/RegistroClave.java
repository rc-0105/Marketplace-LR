package co.edu.unbosque.marketplace.compartido.idempotencia;

import java.time.Instant;

/**
 * Lectura de una fila de {@code clave_idempotencia}. {@code codigoEstado} y {@code cuerpo} solo existen
 * cuando el estado es COMPLETADA.
 */
public record RegistroClave(Estado estado, String huella, Integer codigoEstado, String cuerpo, Instant creadaEn,
        Instant venceEn) {

    public enum Estado { EN_PROCESO, COMPLETADA }
}
