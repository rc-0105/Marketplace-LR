package co.edu.unbosque.marketplace.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Comprueba que la migración V2 deja la tabla de la denylist lista (S-A17). */
@SpringBootTest
class MigracionV2Test {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM usuario");
    }

    @Test
    @DisplayName("Crea la tabla con jti como clave primaria y el índice por expiración")
    void creaTablaEIndice() {
        Integer clavePrimaria = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.key_column_usage
                WHERE table_name = 'sesion_invalidada' AND column_name = 'jti'
                  AND constraint_name LIKE '%pkey'
                """, Integer.class);
        Integer indice = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'sesion_invalidada' AND indexname = 'ix_sesion_invalidada_expira_en'
                """, Integer.class);

        assertThat(clavePrimaria).isEqualTo(1);
        assertThat(indice).isEqualTo(1);
    }

    @Test
    @DisplayName("Al borrar el usuario se borran en cascada sus sesiones invalidadas")
    void borraEnCascada() {
        Long usuarioId = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, correo, contrasena_hash, rol, estado)
                VALUES ('Ana', 'ana@correo.com', 'hash', 'HUESPED', 'ACTIVA') RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO sesion_invalidada (jti, usuario_id, expira_en) VALUES (?, ?, ?)",
                "jti-1", usuarioId, Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion_invalidada", Integer.class)).isZero();
    }
}
