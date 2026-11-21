package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** La limpieza elimina las filas vencidas y respeta las vigentes sobre PostgreSQL real (REQ-IS11). */
@SpringBootTest
class ServicioIdempotenciaLimpiezaIntegracionTest {

    @Autowired
    private ServicioIdempotencia servicio;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE clave_idempotencia");
    }

    @Test
    @DisplayName("Con tres filas (vencida hace una hora, vencida hace un minuto y vigente) quedan 1 y se informan 2")
    void eliminaSoloLasVencidas() {
        UUID vigente = UUID.randomUUID();
        sembrar(UUID.randomUUID(), Duration.ofHours(-1));
        sembrar(UUID.randomUUID(), Duration.ofMinutes(-1));
        sembrar(vigente, Duration.ofHours(1));

        int eliminadas = servicio.limpiarVencidas();

        assertThat(eliminadas).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT clave FROM clave_idempotencia", UUID.class)).containsExactly(vigente);
    }

    private void sembrar(UUID clave, Duration desdeAhora) {
        Instant vence = Instant.now().plus(desdeAhora);
        jdbc.update("""
                INSERT INTO clave_idempotencia
                    (usuario_id, clave, huella, estado, reclamo, codigo_estado, cuerpo, creada_en, vence_en)
                VALUES (2, ?, ?, 'COMPLETADA', ?, 201, '{}', ?, ?)
                """, clave, "a".repeat(64), UUID.randomUUID(), Timestamp.from(vence.minus(Duration.ofHours(24))),
                Timestamp.from(vence));
    }
}
