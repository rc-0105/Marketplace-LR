package co.edu.unbosque.marketplace.auditoria.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiltroAuditoriaTest {

    @ParameterizedTest(name = "[{0}] equivale a ausente")
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("Los textos vacíos o en blanco equivalen a un filtro ausente")
    void blancosSonAusentes(String blanco) {
        FiltroAuditoria filtro = new FiltroAuditoria(blanco, blanco, blanco, blanco, null, null);

        assertThat(filtro.usuario()).isNull();
        assertThat(filtro.evento()).isNull();
        assertThat(filtro.entidad()).isNull();
        assertThat(filtro.entidadId()).isNull();
    }

    @Test
    @DisplayName("Los valores presentes se conservan tal cual, sin recortar ni cambiar mayúsculas")
    void conservaLosValores() {
        FiltroAuditoria filtro = new FiltroAuditoria("2", "reserva", "Reserva", "55",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

        assertThat(filtro).isEqualTo(new FiltroAuditoria("2", "reserva", "Reserva", "55",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11)));
        assertThat(filtro.evento()).isEqualTo("reserva");
    }
}
