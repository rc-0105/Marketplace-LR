package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Esquema y operaciones del registro de claves de idempotencia sobre PostgreSQL (SOF-77, V24). */
@SpringBootTest
class RegistroIdempotenciaIntegracionTest {

    private static final String HUELLA = "a".repeat(64);
    private static final String HUELLA_NUEVA = "b".repeat(64);
    private static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(120);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegistroIdempotencia registro;

    @Autowired
    private PlatformTransactionManager transacciones;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE clave_idempotencia");
    }

    private void insertarEnProceso(long usuario, UUID clave) {
        jdbc.update("""
                INSERT INTO clave_idempotencia (usuario_id, clave, huella, estado, reclamo, creada_en, vence_en)
                VALUES (?, ?, ?, 'EN_PROCESO', ?, now(), now() + interval '2 minutes')
                """, usuario, clave, HUELLA, UUID.randomUUID());
    }

    private void sembrar(long usuario, UUID clave, String estado, Instant creada, Instant vence) {
        boolean completa = "COMPLETADA".equals(estado);
        jdbc.update("""
                INSERT INTO clave_idempotencia
                    (usuario_id, clave, huella, estado, reclamo, codigo_estado, cuerpo, creada_en, vence_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, usuario, clave, HUELLA, estado, UUID.randomUUID(), completa ? 201 : null,
                completa ? "{\"id\": 1}" : null, Timestamp.from(creada), Timestamp.from(vence));
    }

    private Map<String, Object> fila(long usuario, UUID clave) {
        return jdbc.queryForMap("SELECT * FROM clave_idempotencia WHERE usuario_id = ? AND clave = ?", usuario,
                clave);
    }

    private static Instant instante(Map<String, Object> fila, String columna) {
        return ((Timestamp) fila.get(columna)).toInstant();
    }

    private long contar(UUID clave) {
        return jdbc.queryForObject("SELECT count(*) FROM clave_idempotencia WHERE clave = ?", Long.class, clave);
    }

    @Test
    @DisplayName("Rechaza una segunda fila con el mismo usuario y la misma clave")
    void rechazaDuplicado() {
        UUID clave = UUID.randomUUID();
        insertarEnProceso(2L, clave);

        assertThatThrownBy(() -> insertarEnProceso(2L, clave)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Acepta la misma clave con otro usuario")
    void aceptaMismaClaveConOtroUsuario() {
        UUID clave = UUID.randomUUID();
        insertarEnProceso(2L, clave);
        insertarEnProceso(4L, clave);

        assertThat(contar(clave)).isEqualTo(2L);
    }

    @Test
    @DisplayName("Rechaza un estado distinto de EN_PROCESO y COMPLETADA")
    void rechazaEstadoInvalido() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO clave_idempotencia (usuario_id, clave, huella, estado, reclamo, creada_en, vence_en)
                VALUES (2, ?, ?, 'OTRO', ?, now(), now())
                """, UUID.randomUUID(), HUELLA, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza una fila COMPLETADA sin código de estado ni cuerpo")
    void rechazaCompletadaIncompleta() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO clave_idempotencia (usuario_id, clave, huella, estado, reclamo, creada_en, vence_en)
                VALUES (2, ?, ?, 'COMPLETADA', ?, now(), now())
                """, UUID.randomUUID(), HUELLA, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Acepta un usuario inexistente porque la tabla no tiene clave foránea")
    void aceptaUsuarioInexistente() {
        insertarEnProceso(987_654_321L, UUID.randomUUID());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM clave_idempotencia", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Existe el índice sobre vence_en para la limpieza")
    void existeIndiceDeVencimiento() {
        Long indices = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_clave_idempotencia_vence_en'", Long.class);

        assertThat(indices).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sin fila previa reclama la clave y la deja EN_PROCESO con el lease como vencimiento")
    void reclamaUnaClaveLibre() {
        UUID clave = UUID.randomUUID();

        Optional<UUID> reclamo = registro.reclamar(2L, clave, HUELLA, AHORA, LEASE);

        assertThat(reclamo).isPresent();
        Map<String, Object> fila = fila(2L, clave);
        assertThat(fila).containsEntry("estado", "EN_PROCESO").containsEntry("huella", HUELLA)
                .containsEntry("reclamo", reclamo.get());
        assertThat(instante(fila, "creada_en")).isEqualTo(AHORA);
        assertThat(instante(fila, "vence_en")).isEqualTo(AHORA.plus(LEASE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_PROCESO", "COMPLETADA"})
    @DisplayName("Con una fila vigente, en cualquier estado, no reclama y no la modifica")
    void noReclamaUnaClaveVigente(String estado) {
        UUID clave = UUID.randomUUID();
        sembrar(2L, clave, estado, AHORA.minusSeconds(10), AHORA.plusSeconds(60));
        Map<String, Object> antes = fila(2L, clave);

        Optional<UUID> reclamo = registro.reclamar(2L, clave, HUELLA_NUEVA, AHORA, LEASE);

        assertThat(reclamo).isEmpty();
        assertThat(fila(2L, clave)).isEqualTo(antes);
    }

    @Test
    @DisplayName("Una fila vencida se reemplaza con huella y creada_en nuevos y sin respuesta guardada")
    void reemplazaUnaFilaVencida() {
        UUID clave = UUID.randomUUID();
        sembrar(2L, clave, "COMPLETADA", AHORA.minus(Duration.ofHours(25)), AHORA.minus(Duration.ofHours(1)));

        Optional<UUID> reclamo = registro.reclamar(2L, clave, HUELLA_NUEVA, AHORA, LEASE);

        assertThat(reclamo).isPresent();
        Map<String, Object> fila = fila(2L, clave);
        assertThat(fila).containsEntry("estado", "EN_PROCESO").containsEntry("huella", HUELLA_NUEVA)
                .containsEntry("reclamo", reclamo.get()).containsEntry("codigo_estado", null)
                .containsEntry("cuerpo", null);
        assertThat(instante(fila, "creada_en")).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("En la frontera exacta (vence_en igual a ahora) la fila ya está vencida")
    void fronteraExactaEstaVencida() {
        UUID clave = UUID.randomUUID();
        sembrar(2L, clave, "COMPLETADA", AHORA.minusSeconds(86_400), AHORA);

        assertThat(registro.reclamar(2L, clave, HUELLA_NUEVA, AHORA, LEASE)).isPresent();
    }

    @Test
    @DisplayName("Un segundo antes del vencimiento la fila sigue vigente")
    void unSegundoAntesSigueVigente() {
        UUID clave = UUID.randomUUID();
        sembrar(2L, clave, "COMPLETADA", AHORA.minusSeconds(86_399), AHORA.plusSeconds(1));

        assertThat(registro.reclamar(2L, clave, HUELLA_NUEVA, AHORA, LEASE)).isEmpty();
    }

    @Test
    @DisplayName("Un EN_PROCESO con el lease agotado se puede reclamar de nuevo")
    void leaseAgotadoSeReclama() {
        UUID clave = UUID.randomUUID();
        sembrar(2L, clave, "EN_PROCESO", AHORA.minus(LEASE), AHORA);

        Optional<UUID> reclamo = registro.reclamar(2L, clave, HUELLA_NUEVA, AHORA, LEASE);

        assertThat(reclamo).isPresent();
        assertThat(fila(2L, clave)).containsEntry("huella", HUELLA_NUEVA);
    }

    @Test
    @DisplayName("El mismo UUID de otro usuario es un reclamo independiente")
    void reclamoIndependientePorUsuario() {
        UUID clave = UUID.randomUUID();
        sembrar(2L, clave, "COMPLETADA", AHORA.minusSeconds(10), AHORA.plusSeconds(60));

        assertThat(registro.reclamar(4L, clave, HUELLA, AHORA, LEASE)).isPresent();
        assertThat(contar(clave)).isEqualTo(2L);
    }

    @Test
    @DisplayName("El JdbcTemplate participa de la transacción JPA: dos sentencias comparten transacción")
    void jdbcParticipaDeLaTransaccion() {
        List<Long> transaccionesVistas = new TransactionTemplate(transacciones).execute(estado -> List.of(
                jdbc.queryForObject("SELECT txid_current()", Long.class),
                jdbc.queryForObject("SELECT txid_current()", Long.class)));

        assertThat(transaccionesVistas.get(0)).isEqualTo(transaccionesVistas.get(1));
    }

    @Test
    @DisplayName("El reclamo se confirma aparte: sobrevive al rollback de la transacción del llamador")
    void elReclamoSobreviveAlRollbackDelLlamador() {
        UUID clave = UUID.randomUUID();

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            registro.reclamar(2L, clave, HUELLA, AHORA, LEASE);
            estado.setRollbackOnly();
        });

        assertThat(contar(clave)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Buscar una clave inexistente devuelve vacío")
    void buscarInexistente() {
        assertThat(registro.buscar(2L, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Buscar devuelve el estado, la huella y el vencimiento de una fila EN_PROCESO")
    void buscarEnProceso() {
        UUID clave = UUID.randomUUID();
        registro.reclamar(2L, clave, HUELLA, AHORA, LEASE);

        RegistroClave encontrado = registro.buscar(2L, clave).orElseThrow();

        assertThat(encontrado.estado()).isEqualTo(RegistroClave.Estado.EN_PROCESO);
        assertThat(encontrado.huella()).isEqualTo(HUELLA);
        assertThat(encontrado.codigoEstado()).isNull();
        assertThat(encontrado.cuerpo()).isNull();
        assertThat(encontrado.creadaEn()).isEqualTo(AHORA);
        assertThat(encontrado.venceEn()).isEqualTo(AHORA.plus(LEASE));
    }

    @Test
    @DisplayName("Buscar no ve las claves de otro usuario")
    void buscarEsPorUsuario() {
        UUID clave = UUID.randomUUID();
        registro.reclamar(2L, clave, HUELLA, AHORA, LEASE);

        assertThat(registro.buscar(4L, clave)).isEmpty();
    }

    @Test
    @DisplayName("Completar con el reclamo propio guarda código y cuerpo, y vence a creada_en más la vigencia")
    void completarConReclamoPropio() {
        UUID clave = UUID.randomUUID();
        UUID reclamo = registro.reclamar(2L, clave, HUELLA, AHORA, LEASE).orElseThrow();

        boolean completada = registro.completar(2L, clave, reclamo, 201, "{\"id\": 7}", Duration.ofHours(24));

        assertThat(completada).isTrue();
        RegistroClave encontrado = registro.buscar(2L, clave).orElseThrow();
        assertThat(encontrado.estado()).isEqualTo(RegistroClave.Estado.COMPLETADA);
        assertThat(encontrado.codigoEstado()).isEqualTo(201);
        assertThat(encontrado.cuerpo()).isEqualTo("{\"id\": 7}");
        assertThat(encontrado.venceEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Completar con un reclamo ajeno devuelve false y no modifica la fila")
    void completarConReclamoAjeno() {
        UUID clave = UUID.randomUUID();
        registro.reclamar(2L, clave, HUELLA, AHORA, LEASE);
        Map<String, Object> antes = fila(2L, clave);

        boolean completada = registro.completar(2L, clave, UUID.randomUUID(), 201, "{}", Duration.ofHours(24));

        assertThat(completada).isFalse();
        assertThat(fila(2L, clave)).isEqualTo(antes);
    }

    @Test
    @DisplayName("El cuerpo se guarda y se lee idéntico: espacios y orden de claves intactos")
    void cuerpoIdenticoByteAByte() {
        UUID clave = UUID.randomUUID();
        UUID reclamo = registro.reclamar(2L, clave, HUELLA, AHORA, LEASE).orElseThrow();
        String cuerpo = "{ \"zeta\": 1,  \"alfa\" :[3, 1,2],\n \"ñandú\":\"árbol\" }";

        registro.completar(2L, clave, reclamo, 201, cuerpo, Duration.ofHours(24));

        assertThat(registro.buscar(2L, clave).orElseThrow().cuerpo()).isEqualTo(cuerpo);
    }

    @Test
    @DisplayName("Liberar con el reclamo propio elimina la fila")
    void liberarConReclamoPropio() {
        UUID clave = UUID.randomUUID();
        UUID reclamo = registro.reclamar(2L, clave, HUELLA, AHORA, LEASE).orElseThrow();

        assertThat(registro.liberar(2L, clave, reclamo)).isTrue();
        assertThat(contar(clave)).isZero();
    }

    @Test
    @DisplayName("Liberar con un reclamo ajeno no elimina la fila")
    void liberarConReclamoAjeno() {
        UUID clave = UUID.randomUUID();
        registro.reclamar(2L, clave, HUELLA, AHORA, LEASE);

        assertThat(registro.liberar(2L, clave, UUID.randomUUID())).isFalse();
        assertThat(contar(clave)).isEqualTo(1L);
    }
}
