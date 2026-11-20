package co.edu.unbosque.marketplace.auditoria.model;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventoAuditoriaTest {

    private static final Instant FECHA = Instant.parse("2026-11-16T15:00:00Z");

    @Test
    @DisplayName("La fábrica copia los campos del evento y guarda la acción como nombre del enum")
    void copiaLosCamposDelEvento() {
        EventoCritico evento = new EventoCritico("2", AccionCritica.CANCELACION, "Reserva", "55",
                ResultadoEvento.FALLO, FECHA, Map.of("operacion", "CANCELAR"));

        EventoAuditoria fila = EventoAuditoria.de(evento, Map.of("operacion", "CANCELAR"), "c-3");

        assertThat(fila.getId()).isNull();
        assertThat(fila.getUsuario()).isEqualTo("2");
        assertThat(fila.getAccion()).isEqualTo("CANCELACION");
        assertThat(fila.getEntidad()).isEqualTo("Reserva");
        assertThat(fila.getEntidadId()).isEqualTo("55");
        assertThat(fila.getResultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(fila.getFecha()).isEqualTo(FECHA);
        assertThat(fila.getDatos()).containsEntry("operacion", "CANCELAR");
        assertThat(fila.getCorrelacion()).isEqualTo("c-3");
    }

    @Test
    @DisplayName("Acepta correlación y entidadId nulos")
    void aceptaNulos() {
        EventoCritico evento = new EventoCritico(null, AccionCritica.AUTENTICACION, "Usuario", null,
                ResultadoEvento.EXITO, FECHA, null);

        EventoAuditoria fila = EventoAuditoria.de(evento, Map.of(), null);

        assertThat(fila.getCorrelacion()).isNull();
        assertThat(fila.getEntidadId()).isNull();
    }
}
