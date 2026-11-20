package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Esquema y operaciones del registro de claves de idempotencia sobre PostgreSQL (SOF-77, V24). */
@SpringBootTest
class RegistroIdempotenciaIntegracionTest {

    private static final String HUELLA = "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

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

        assertThat(jdbc.queryForObject("SELECT count(*) FROM clave_idempotencia WHERE clave = ?", Long.class, clave))
                .isEqualTo(2L);
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
}
