package co.edu.unbosque.marketplace.compartido.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Detección de una restricción de exclusión de PostgreSQL violada (SQLState 23P01). */
class RestriccionesPostgresTest {

    @Test
    @DisplayName("Reconoce la violación de la restricción de exclusión por su nombre, aunque venga envuelta")
    void reconoceLaExclusionPorNombre() {
        assertThat(RestriccionesPostgres.violaExclusion(violacion("reserva_sin_cruce", "23P01"), "reserva_sin_cruce"))
                .isTrue();
    }

    @Test
    @DisplayName("No confunde otra restricción ni otro SQLState con la de exclusión pedida")
    void ignoraOtrasRestricciones() {
        assertThat(RestriccionesPostgres.violaExclusion(violacion("otra_sin_cruce", "23P01"), "reserva_sin_cruce"))
                .isFalse();
        assertThat(RestriccionesPostgres.violaExclusion(violacion("reserva_sin_cruce", "23514"), "reserva_sin_cruce"))
                .isFalse();
        assertThat(RestriccionesPostgres.violaExclusion(new IllegalStateException("x"), "reserva_sin_cruce"))
                .isFalse();
    }

    private static DataIntegrityViolationException violacion(String restriccion, String estadoSql) {
        SQLException sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"%s\"".formatted(restriccion),
                estadoSql);
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, null));
    }
}
