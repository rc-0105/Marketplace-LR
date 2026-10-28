package co.edu.unbosque.marketplace.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Comprueba que la migración V5 agrega ELIMINADA y que la base exige la anonimización (REQ-E1). */
@SpringBootTest
class MigracionV5Test {

    private static final String ELIMINAR = """
            UPDATE usuario SET estado = 'ELIMINADA', nombre = ?, correo = ?, motivo_restriccion = ? WHERE id = ?
            """;

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
    @DisplayName("El CHECK de estado acepta exactamente ACTIVA, DESACTIVADA, RESTRINGIDA y ELIMINADA")
    void checkDeEstadoAceptaCuatroValores() {
        String definicion = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'usuario_estado_check' AND conrelid = 'usuario'::regclass
                """, String.class);

        assertThat(Pattern.compile("'([A-Z]+)'::").matcher(definicion).results().map(r -> r.group(1)).toList())
                .containsExactlyInAnyOrder("ACTIVA", "DESACTIVADA", "RESTRINGIDA", "ELIMINADA");
    }

    @Test
    @DisplayName("Una cuenta ELIMINADA anónima se guarda")
    void aceptaEliminadaAnonima() {
        int filas = jdbc.update(ELIMINAR, "Usuario eliminado", correoAnonimo(), null, usuarioId);

        assertThat(filas).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        "Ana,true,NULL",
        "Usuario eliminado,false,NULL",
        "Usuario eliminado,true,Pagos en disputa"})
    @DisplayName("Una cuenta ELIMINADA con nombre, correo o motivo sin anonimizar viola el CHECK")
    void rechazaEliminadaSinAnonimizar(String nombre, boolean correoAnonimo, String motivo) {
        String correo = correoAnonimo ? correoAnonimo() : "ana@correo.com";

        assertThatThrownBy(() -> jdbc.update(ELIMINAR, nombre, correo, motivo, usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String correoAnonimo() {
        return "eliminada-" + usuarioId + "@eliminada.invalid";
    }
}
