package co.edu.unbosque.marketplace.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Comprueba que la migración V4 amplía los estados de cuenta y exige el motivo al restringir (REQ-E1). */
@SpringBootTest
class MigracionV4Test {

    @Autowired
    private JdbcTemplate jdbc;

    private Long usuarioId;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM usuario");
        usuarioId = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, correo, contrasena_hash, rol, estado)
                VALUES ('Ana', 'ana@correo.com', 'hash', 'HUESPED', 'ACTIVA') RETURNING id
                """, Long.class);
    }

    @Test
    @DisplayName("El CHECK de estado acepta exactamente ACTIVA, DESACTIVADA y RESTRINGIDA")
    void checkDeEstadoAceptaTresValores() {
        String definicion = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'usuario_estado_check' AND conrelid = 'usuario'::regclass
                """, String.class);

        assertThat(Pattern.compile("'([A-Z]+)'::").matcher(definicion).results().map(r -> r.group(1)).toList())
                .containsExactlyInAnyOrder("ACTIVA", "DESACTIVADA", "RESTRINGIDA");
        for (String estado : List.of("ACTIVA", "DESACTIVADA")) {
            assertThat(jdbc.update("UPDATE usuario SET estado = ? WHERE id = ?", estado, usuarioId)).isEqualTo(1);
        }
        assertThat(jdbc.update(
                "UPDATE usuario SET estado = 'RESTRINGIDA', motivo_restriccion = 'x' WHERE id = ?", usuarioId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un estado desconocido viola el CHECK de estado")
    void rechazaEstadoDesconocido() {
        assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una cuenta DESACTIVADA se guarda sin motivo de restricción")
    void aceptaDesactivadaSinMotivo() {
        int filas = jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA' WHERE id = ?", usuarioId);

        assertThat(filas).isEqualTo(1);
    }

    @Test
    @DisplayName("Restringir sin motivo viola el CHECK y con motivo se guarda")
    void restringirExigeMotivo() {
        assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET estado = 'RESTRINGIDA' WHERE id = ?", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class);

        int filas = jdbc.update(
                "UPDATE usuario SET estado = 'RESTRINGIDA', motivo_restriccion = 'Pagos en disputa' WHERE id = ?",
                usuarioId);

        assertThat(filas).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT motivo_restriccion FROM usuario WHERE id = ?", String.class,
                usuarioId)).isEqualTo("Pagos en disputa");
    }

    @Test
    @DisplayName("El motivo de restricción admite hasta 500 caracteres y rechaza 501")
    void limitaElMotivoA500() {
        assertThat(jdbc.update("UPDATE usuario SET motivo_restriccion = ? WHERE id = ?", "a".repeat(500),
                usuarioId)).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET motivo_restriccion = ? WHERE id = ?",
                "a".repeat(501), usuarioId)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
