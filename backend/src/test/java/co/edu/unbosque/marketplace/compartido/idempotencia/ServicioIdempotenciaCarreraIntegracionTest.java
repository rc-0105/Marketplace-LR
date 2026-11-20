package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.SolicitudEnProcesoException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ocho hilos contra PostgreSQL real con una operación lenta: la operación corre una sola vez (REQ-IS2). */
@SpringBootTest
class ServicioIdempotenciaCarreraIntegracionTest {

    private static final int HILOS = 8;
    private static final String OPERACION = "POST /prueba";

    @Autowired
    private ServicioIdempotencia servicio;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE clave_idempotencia");
    }

    @Test
    @DisplayName("Con la misma clave, la operación lenta corre una vez: un éxito y el resto en proceso o réplica")
    void unaSolaEjecucion() throws Exception {
        UUID clave = UUID.randomUUID();

        Resultado resultado = dispararEnParalelo(clave);

        assertThat(resultado.ejecuciones()).isEqualTo(1);
        assertThat(resultado.originales()).isEqualTo(1);
        assertThat(resultado.originales() + resultado.replicas() + resultado.enProceso()).isEqualTo(HILOS);
        assertThat(resultado.otrosFallos()).isZero();
        assertThat(filas(clave)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Con una fila vencida sembrada y ocho hilos, exactamente uno reclama y ejecuta")
    void unaSolaEjecucionSobreFilaVencida() throws Exception {
        UUID clave = UUID.randomUUID();
        Instant hace25Horas = Instant.now().minus(Duration.ofHours(25));
        jdbc.update("""
                INSERT INTO clave_idempotencia
                    (usuario_id, clave, huella, estado, reclamo, codigo_estado, cuerpo, creada_en, vence_en)
                VALUES (2, ?, ?, 'COMPLETADA', ?, 201, '{}', ?, ?)
                """, clave, "a".repeat(64), UUID.randomUUID(), Timestamp.from(hace25Horas),
                Timestamp.from(Instant.now().minus(Duration.ofHours(1))));

        Resultado resultado = dispararEnParalelo(clave);

        assertThat(resultado.ejecuciones()).isEqualTo(1);
        assertThat(resultado.originales()).isEqualTo(1);
        assertThat(resultado.otrosFallos()).isZero();
    }

    private long filas(UUID clave) {
        return jdbc.queryForObject("SELECT count(*) FROM clave_idempotencia WHERE clave = ?", Long.class, clave);
    }

    private Resultado dispararEnParalelo(UUID clave) throws Exception {
        AtomicInteger ejecuciones = new AtomicInteger();
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            SolicitudIdempotente solicitud = new SolicitudIdempotente(2L, clave.toString(), OPERACION, "contenido");
            List<Future<RespuestaIdempotente>> intentos = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                intentos.add(hilos.submit(() -> {
                    salida.await();
                    return servicio.ejecutar(solicitud, HttpStatus.CREATED, () -> {
                        ejecuciones.incrementAndGet();
                        dormir();
                        return Map.of("ok", true);
                    });
                }));
            }
            salida.countDown();
            return contar(intentos, ejecuciones);
        } finally {
            hilos.shutdownNow();
        }
    }

    private static Resultado contar(List<Future<RespuestaIdempotente>> intentos, AtomicInteger ejecuciones)
            throws InterruptedException {
        int originales = 0;
        int replicas = 0;
        int enProceso = 0;
        int otros = 0;
        for (Future<RespuestaIdempotente> intento : intentos) {
            try {
                if (intento.get().replicada()) {
                    replicas++;
                } else {
                    originales++;
                }
            } catch (ExecutionException error) {
                if (error.getCause() instanceof SolicitudEnProcesoException) {
                    enProceso++;
                } else {
                    otros++;
                }
            }
        }
        return new Resultado(ejecuciones.get(), originales, replicas, enProceso, otros);
    }

    private static void dormir() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private record Resultado(int ejecuciones, int originales, int replicas, int enProceso, int otrosFallos) {
    }
}
