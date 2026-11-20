package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.auditoria.services.AuditoriaService;
import co.edu.unbosque.marketplace.auditoria.services.RepositorioEventosFalso;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.infraestructura.web.FiltroCorrelacion;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class PersistenciaEventosCriticosTest {

    private static final Instant FECHA = Instant.parse("2026-11-16T15:00:00Z");

    private final RepositorioEventosFalso repositorio = new RepositorioEventosFalso();
    private final Logger logger = (Logger) LoggerFactory.getLogger(PersistenciaEventosCriticos.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void limpiar() {
        logger.detachAppender(logs);
        MDC.clear();
    }

    @Test
    @DisplayName("Persiste los datos saneados igual que el log de auditoría")
    void saneaLosDatos() {
        persistencia().persistir(evento("2", "55", Map.of("operacion", "LOGIN", "password", "secreto")));

        EventoAuditoria fila = repositorio.guardados.get(0);

        assertThat(fila.getDatos()).containsEntry("operacion", "LOGIN")
                .containsEntry("password", SanitizadorDatos.OCULTO)
                .doesNotContainValue("secreto");
    }

    @Test
    @DisplayName("Toma la correlación del MDC y, sin ella, guarda null")
    void correlacionDelMdc() {
        MDC.put(FiltroCorrelacion.CLAVE_MDC, "abc-123");
        persistencia().persistir(evento("2", "55", Map.of()));
        MDC.clear();
        persistencia().persistir(evento("2", "55", Map.of()));

        assertThat(repositorio.guardados).extracting(EventoAuditoria::getCorrelacion)
                .containsExactly("abc-123", null);
    }

    @Test
    @DisplayName("Un evento sin entidadId se persiste igual")
    void aceptaEntidadIdNulo() {
        persistencia().persistir(evento("2", null, Map.of()));

        assertThat(repositorio.guardados).singleElement().satisfies(fila -> {
            assertThat(fila.getEntidadId()).isNull();
            assertThat(fila.getAccion()).isEqualTo("RESERVA");
        });
    }

    @Test
    @DisplayName("Si guardar falla no propaga y deja un ERROR sin los datos del evento")
    void toleraElFallo() {
        RepositorioEventosFalso roto = new RepositorioEventosFalso() {
            @Override
            public EventoAuditoria save(EventoAuditoria evento) {
                throw new IllegalStateException("base caida");
            }
        };
        PersistenciaEventosCriticos persistencia = new PersistenciaEventosCriticos(new AuditoriaService(roto));
        EventoCritico evento = evento("2", "55", Map.of("operacion", "CREAR", "nota", "dato-privado"));

        assertThatCode(() -> persistencia.persistir(evento)).doesNotThrowAnyException();

        assertThat(logs.list).singleElement().satisfies(registro -> {
            assertThat(registro.getLevel()).isEqualTo(Level.ERROR);
            assertThat(registro.getFormattedMessage()).contains("RESERVA", "Reserva", "55")
                    .doesNotContain("dato-privado");
        });
    }

    private PersistenciaEventosCriticos persistencia() {
        return new PersistenciaEventosCriticos(new AuditoriaService(repositorio));
    }

    private static EventoCritico evento(String usuario, String entidadId, Map<String, Object> datos) {
        return new EventoCritico(usuario, AccionCritica.RESERVA, "Reserva", entidadId, ResultadoEvento.EXITO,
                FECHA, datos);
    }
}
