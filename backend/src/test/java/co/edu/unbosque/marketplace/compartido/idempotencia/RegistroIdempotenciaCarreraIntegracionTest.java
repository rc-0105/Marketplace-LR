package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ocho hilos sobre PostgreSQL real: el reclamo de una misma clave tiene exactamente un ganador (REQ-IS2). */
@SpringBootTest
class RegistroIdempotenciaCarreraIntegracionTest {

    private static final int HILOS = 8;
    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(120);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegistroIdempotencia registro;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE clave_idempotencia");
    }

    @Test
    @DisplayName("Con ocho hilos sobre la misma clave libre hay un solo ganador y una sola fila")
    void unSoloGanadorSobreClaveLibre() throws Exception {
        UUID clave = UUID.randomUUID();

        assertThat(reclamarEnParalelo(clave)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clave_idempotencia WHERE clave = ?", Long.class,
                clave)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Con ocho hilos sobre una fila vencida también gana exactamente uno")
    void unSoloGanadorSobreFilaVencida() throws Exception {
        UUID clave = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO clave_idempotencia
                    (usuario_id, clave, huella, estado, reclamo, codigo_estado, cuerpo, creada_en, vence_en)
                VALUES (2, ?, ?, 'COMPLETADA', ?, 201, '{}', ?, ?)
                """, clave, "a".repeat(64), UUID.randomUUID(), Timestamp.from(AHORA.minus(Duration.ofHours(25))),
                Timestamp.from(AHORA.minus(Duration.ofHours(1))));

        assertThat(reclamarEnParalelo(clave)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM clave_idempotencia WHERE clave = ?", String.class,
                clave)).isEqualTo("EN_PROCESO");
    }

    private int reclamarEnParalelo(UUID clave) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Optional<UUID>>> intentos = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                String huella = String.valueOf(i).repeat(64).substring(0, 64);
                intentos.add(hilos.submit(() -> {
                    salida.await();
                    return registro.reclamar(2L, clave, huella, AHORA, LEASE);
                }));
            }
            salida.countDown();
            int ganadores = 0;
            for (Future<Optional<UUID>> intento : intentos) {
                if (intento.get().isPresent()) {
                    ganadores++;
                }
            }
            return ganadores;
        } finally {
            hilos.shutdownNow();
        }
    }
}
