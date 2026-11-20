package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.auditoria.repository.EventoAuditoriaRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Si la inserción falla, el negocio sigue y queda el respaldo en el log (REQ-RA6). */
// Pool chico: este contexto propio se suma a los demas y PostgreSQL limita las conexiones totales.
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=3")
class AuditoriaToleranciaIntegracionTest {

    @MockBean
    private EventoAuditoriaRepository repositorio;

    @Autowired
    private PublicadorEventos publicador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("Un fallo al guardar no rompe la operación, deja ERROR y el log JSON se emite")
    void toleraElFalloDeInsercion() {
        when(repositorio.save(any(EventoAuditoria.class))).thenThrow(new IllegalStateException("base caida"));
        Logger logger = (Logger) LoggerFactory.getLogger(PersistenciaEventosCriticos.class);
        ListAppender<ILoggingEvent> errores = new ListAppender<>();
        errores.start();
        logger.addAppender(errores);

        try (var captura = new CapturaAuditoria()) {
            EventoCritico evento = new EventoCritico("2", AccionCritica.RESERVA, "Reserva", "55",
                    ResultadoEvento.EXITO, Instant.parse("2026-11-16T15:00:00Z"), Map.of("operacion", "CREAR"));

            assertThatCode(() -> publicador.publicar(evento)).doesNotThrowAnyException();

            assertThat(captura.cantidad()).isEqualTo(1);
        } finally {
            logger.detachAppender(errores);
        }
        assertThat(errores.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
    }

    @Test
    @DisplayName("Un fallo al guardar la auditoría no revierte la transacción de negocio que publica")
    void elNegocioConfirmaAunqueFalleLaAuditoria() {
        when(repositorio.save(any(EventoAuditoria.class))).thenThrow(new IllegalStateException("base caida"));
        jdbc.execute("TRUNCATE evento_auditoria RESTART IDENTITY");
        EventoCritico evento = new EventoCritico("2", AccionCritica.RESERVA, "Reserva", "55",
                ResultadoEvento.EXITO, Instant.parse("2026-11-16T15:00:00Z"), Map.of("operacion", "CREAR"));
        BigDecimal original = jdbc.queryForObject("SELECT porcentaje_servicio FROM configuracion_cotizacion",
                BigDecimal.class);
        try {
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
                jdbc.update("UPDATE configuracion_cotizacion SET porcentaje_servicio = 55");
                publicador.publicar(evento);
            });

            assertThat(jdbc.queryForObject("SELECT porcentaje_servicio FROM configuracion_cotizacion",
                    BigDecimal.class)).isEqualByComparingTo("55");
            verify(repositorio).save(any(EventoAuditoria.class));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria", Integer.class)).isZero();
        } finally {
            jdbc.update("UPDATE configuracion_cotizacion SET porcentaje_servicio = ?", original);
        }
    }
}
