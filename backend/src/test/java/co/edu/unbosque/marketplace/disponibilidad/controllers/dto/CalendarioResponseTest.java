package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.disponibilidad.model.Calendario;
import co.edu.unbosque.marketplace.disponibilidad.model.DiaCalendario;
import co.edu.unbosque.marketplace.disponibilidad.model.EstadoDia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendarioResponseTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("La respuesta copia la ventana efectiva, tienePeriodos y el estado de cada día como texto")
    void copiaElCalendarioDelModelo() {
        Calendario calendario = new Calendario(10L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), true,
                List.of(new DiaCalendario(LocalDate.of(2026, 12, 1), EstadoDia.DISPONIBLE, null),
                        new DiaCalendario(LocalDate.of(2026, 12, 2), EstadoDia.RESERVADO, 77L)));

        CalendarioResponse respuesta = CalendarioResponse.desde(calendario);

        assertThat(respuesta.publicacionId()).isEqualTo(10L);
        assertThat(respuesta.desde()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(respuesta.hasta()).isEqualTo(LocalDate.of(2026, 12, 3));
        assertThat(respuesta.tienePeriodos()).isTrue();
        assertThat(respuesta.dias()).containsExactly(
                new DiaCalendarioResponse(LocalDate.of(2026, 12, 1), "DISPONIBLE", null),
                new DiaCalendarioResponse(LocalDate.of(2026, 12, 2), "RESERVADO", 77L));
    }

    @Test
    @DisplayName("En el JSON un día sin reservaId no trae la propiedad y uno con reservaId sí")
    void elJsonOmiteReservaIdCuandoNoHay() throws Exception {
        Calendario calendario = new Calendario(10L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), false,
                List.of(new DiaCalendario(LocalDate.of(2026, 12, 1), EstadoDia.BLOQUEADO, null),
                        new DiaCalendario(LocalDate.of(2026, 12, 2), EstadoDia.RESERVADO, 77L)));

        JsonNode dias = json.readTree(json.writeValueAsString(CalendarioResponse.desde(calendario))).get("dias");

        assertThat(dias).hasSize(2);
        assertThat(dias.get(0).has("reservaId")).isFalse();
        assertThat(dias.get(0).get("fecha").asText()).isEqualTo("2026-12-01");
        assertThat(dias.get(0).get("estado").asText()).isEqualTo("BLOQUEADO");
        assertThat(dias.get(1).get("reservaId").asLong()).isEqualTo(77L);
    }
}
