package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** La fila de auditoría vive en su propia transacción (REQ-RA5, D1, D2). */
// Pool chico: este contexto propio se suma a los demas y PostgreSQL limita las conexiones totales.
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=3")
@Import(AuditoriaTransaccionIntegracionTest.Configuracion.class)
class AuditoriaTransaccionIntegracionTest {

    private static final String PORCENTAJE = "SELECT porcentaje_servicio FROM configuracion_cotizacion";

    @Autowired
    private OperacionDeNegocio negocio;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vaciar() {
        jdbc.execute("TRUNCATE evento_auditoria RESTART IDENTITY");
    }

    @Test
    @DisplayName("El FALLO publicado antes de un rollback de negocio queda registrado")
    void elFalloSobreviveAlRollback() {
        assertThatThrownBy(() -> negocio.publicarFalloYFallar()).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForList("SELECT resultado FROM evento_auditoria", String.class))
                .containsExactly("FALLO");
    }

    @Test
    @DisplayName("El rollback revierte el negocio pero no la fila de auditoría")
    void elRollbackNoArrastraLaAuditoria() {
        assertThatThrownBy(() -> negocio.modificarPublicarYFallar()).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(PORCENTAJE, BigDecimal.class)).isEqualByComparingTo("10");
        // Limitación aceptada (REQ-RA5): un EXITO publicado antes del rollback queda registrado
        // aunque la operación no se haya confirmado ("EXITO fantasma").
        assertThat(jdbc.queryForList("SELECT resultado FROM evento_auditoria", String.class))
                .containsExactly("EXITO");
    }

    @TestConfiguration
    static class Configuracion {

        @Bean
        OperacionDeNegocio operacionDeNegocio(PublicadorEventos publicador, JdbcTemplate jdbc) {
            return new OperacionDeNegocio(publicador, jdbc);
        }
    }

    /** Servicio de negocio de prueba con su propia transacción. */
    static class OperacionDeNegocio {

        private final PublicadorEventos publicador;
        private final JdbcTemplate jdbc;

        OperacionDeNegocio(PublicadorEventos publicador, JdbcTemplate jdbc) {
            this.publicador = publicador;
            this.jdbc = jdbc;
        }

        @Transactional
        void publicarFalloYFallar() {
            publicador.publicar(evento(ResultadoEvento.FALLO));
            throw new IllegalStateException("la operacion fallo");
        }

        @Transactional
        void modificarPublicarYFallar() {
            jdbc.update("UPDATE configuracion_cotizacion SET porcentaje_servicio = 55");
            publicador.publicar(evento(ResultadoEvento.EXITO));
            throw new IllegalStateException("la operacion fallo");
        }

        private static EventoCritico evento(ResultadoEvento resultado) {
            return new EventoCritico("2", AccionCritica.RESERVA, "Reserva", "55", resultado,
                    Instant.parse("2026-11-16T15:00:00Z"), Map.of("operacion", "CREAR"));
        }
    }
}
