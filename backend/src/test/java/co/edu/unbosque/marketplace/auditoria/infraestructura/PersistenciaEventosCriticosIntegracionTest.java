package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.infraestructura.web.FiltroCorrelacion;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Del puerto de publicación a la fila en PostgreSQL (REQ-RA2 a REQ-RA4, REQ-RA8). */
@SpringBootTest
class PersistenciaEventosCriticosIntegracionTest {

    private static final Instant FECHA = Instant.parse("2026-11-16T15:00:00Z");

    @Autowired
    private PublicadorEventos publicador;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vaciar() {
        jdbc.execute("TRUNCATE evento_auditoria RESTART IDENTITY");
    }

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Un evento de reserva queda como una fila con todos sus campos")
    void persisteElEvento() {
        publicador.publicar(new EventoCritico("2", AccionCritica.RESERVA, "Reserva", "55", ResultadoEvento.EXITO,
                FECHA, Map.of("operacion", "CREAR")));

        Map<String, Object> fila = jdbc.queryForMap("SELECT usuario, accion, entidad, entidad_id, resultado, "
                + "datos ->> 'operacion' AS operacion, fecha = '2026-11-16T15:00:00Z'::timestamptz AS misma_fecha "
                + "FROM evento_auditoria");

        assertThat(fila).containsEntry("usuario", "2").containsEntry("accion", "RESERVA")
                .containsEntry("entidad", "Reserva").containsEntry("entidad_id", "55")
                .containsEntry("resultado", "EXITO").containsEntry("operacion", "CREAR")
                .containsEntry("misma_fecha", true);
    }

    @Test
    @DisplayName("Sin usuario ni entidadId la fila queda con anonimo y entidadId nulo")
    void persisteLosNulos() {
        publicador.publicar(new EventoCritico(null, AccionCritica.AUTENTICACION, "Usuario", null,
                ResultadoEvento.FALLO, FECHA, Map.of()));

        Map<String, Object> fila = jdbc.queryForMap("SELECT usuario, entidad_id FROM evento_auditoria");

        assertThat(fila).containsEntry("usuario", "anonimo").containsEntry("entidad_id", null);
    }

    @Test
    @DisplayName("Cada acción crítica deja una fila con el nombre del enum")
    void unaFilaPorAccion() {
        for (AccionCritica accion : AccionCritica.values()) {
            publicador.publicar(evento(accion, ResultadoEvento.EXITO, Map.of()));
        }

        List<String> acciones = jdbc.queryForList("SELECT accion FROM evento_auditoria ORDER BY id", String.class);

        assertThat(acciones).containsExactly(Arrays.stream(AccionCritica.values()).map(Enum::name)
                .toArray(String[]::new));
    }

    @Test
    @DisplayName("EXITO y FALLO se persisten con su resultado")
    void ambosResultados() {
        publicador.publicar(evento(AccionCritica.RESERVA, ResultadoEvento.EXITO, Map.of()));
        publicador.publicar(evento(AccionCritica.RESERVA, ResultadoEvento.FALLO, Map.of()));

        assertThat(jdbc.queryForList("SELECT resultado FROM evento_auditoria ORDER BY id", String.class))
                .containsExactly("EXITO", "FALLO");
    }

    @Test
    @DisplayName("El log JSON de auditoría sigue emitiéndose y además hay fila")
    void logIntacto() {
        try (var captura = new CapturaAuditoria()) {
            publicador.publicar(evento(AccionCritica.RESERVA, ResultadoEvento.EXITO, Map.of("operacion", "CREAR")));

            assertThat(captura.cantidad()).isEqualTo(1);
            assertThat(captura.texto(0)).contains("evento_critico");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("La clave sensible no llega en claro a la fila")
    void saneaDeExtremoAExtremo() {
        publicador.publicar(evento(AccionCritica.AUTENTICACION, ResultadoEvento.EXITO,
                Map.of("operacion", "LOGIN", "password", "secreto")));

        String datos = jdbc.queryForObject("SELECT datos::text FROM evento_auditoria", String.class);

        assertThat(datos).doesNotContain("secreto").contains("LOGIN", SanitizadorDatos.OCULTO);
    }

    @Test
    @DisplayName("Dos eventos con el mismo MDC comparten correlación y sin MDC queda nula")
    void correlacion() {
        MDC.put(FiltroCorrelacion.CLAVE_MDC, "abc-123");
        publicador.publicar(evento(AccionCritica.RESERVA, ResultadoEvento.EXITO, Map.of()));
        publicador.publicar(evento(AccionCritica.CANCELACION, ResultadoEvento.EXITO, Map.of()));
        MDC.clear();
        publicador.publicar(evento(AccionCritica.RESENA, ResultadoEvento.EXITO, Map.of()));

        List<String> correlaciones = jdbc.query("SELECT correlacion FROM evento_auditoria ORDER BY id",
                (rs, fila) -> rs.getString(1));

        assertThat(correlaciones).containsExactly("abc-123", "abc-123", null);
    }

    private static EventoCritico evento(AccionCritica accion, ResultadoEvento resultado, Map<String, Object> datos) {
        return new EventoCritico("2", accion, "Reserva", "55", resultado, FECHA, datos);
    }
}
