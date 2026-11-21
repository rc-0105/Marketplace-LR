package co.edu.unbosque.marketplace.resenas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La reseña nace visible y solo admite calificaciones de 1 a 5 (SOF-80, HU-35). */
class ResenaTest {

    private static final Instant AHORA = Instant.parse("2026-12-20T15:00:00Z");

    @Test
    @DisplayName("una reseña nueva nace VISIBLE")
    void nacceVisible() {
        Resena resena = Resena.nueva(1L, 2L, 3L, AHORA, 5, "Excelente");

        assertThat(resena.getEstado()).isEqualTo(EstadoResena.VISIBLE);
    }

    @Test
    @DisplayName("las calificaciones de 1 a 5 se aceptan")
    void aceptaElRango() {
        for (int calificacion = 1; calificacion <= 5; calificacion++) {
            assertThat(Resena.nueva(1L, 2L, 3L, AHORA, calificacion, "Comentario").getCalificacion())
                    .isEqualTo(calificacion);
        }
    }

    @Test
    @DisplayName("una calificación fuera de 1 a 5 se rechaza")
    void rechazaFueraDelRango() {
        assertThatThrownBy(() -> Resena.nueva(1L, 2L, 3L, AHORA, 0, "Comentario"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Resena.nueva(1L, 2L, 3L, AHORA, 6, "Comentario"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
