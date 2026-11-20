package co.edu.unbosque.marketplace.compartido.infraestructura;

import java.sql.SQLException;

/** Utilidades para reconocer qué restricción de PostgreSQL rechazó una escritura. */
public final class RestriccionesPostgres {

    private static final String VIOLACION_EXCLUSION = "23P01";

    private RestriccionesPostgres() {
    }

    /**
     * Hibernate no extrae el nombre de una restricción de exclusión (SQLState 23P01), así que se
     * recorre la cadena de causas y se busca el nombre en el mensaje del error de PostgreSQL.
     */
    public static boolean violaExclusion(Throwable error, String restriccion) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && VIOLACION_EXCLUSION.equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains(restriccion)) {
                return true;
            }
        }
        return false;
    }
}
