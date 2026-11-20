package co.edu.unbosque.marketplace.auditoria.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** La tabla evento_auditoria sobre PostgreSQL: esquema permisivo y de solo inserción (REQ-RA1, REQ-RA7). */
@SpringBootTest
class EventoAuditoriaRepositoryIntegracionTest {

    private static final String INSERTAR = "INSERT INTO evento_auditoria (fecha, usuario, accion, entidad, resultado) "
            + "VALUES (now(), ?, ?, 'Reserva', ?)";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vaciar() {
        jdbc.execute("TRUNCATE evento_auditoria RESTART IDENTITY");
    }

    @Test
    @DisplayName("Acepta un usuario inexistente y una acción nueva: no hay FK ni CHECK sobre ellos")
    void esquemaPermisivo() {
        jdbc.update(INSERTAR, "999", "VALOR_NUEVO", "EXITO");

        assertThat(jdbc.queryForObject("SELECT accion FROM evento_auditoria WHERE usuario = '999'", String.class))
                .isEqualTo("VALOR_NUEVO");
    }

    @Test
    @DisplayName("Rechaza un resultado distinto de EXITO o FALLO")
    void resultadoInvalido() {
        assertThatThrownBy(() -> jdbc.update(INSERTAR, "2", "RESERVA", "TAL_VEZ"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Los datos quedan en {} cuando no se informan")
    void datosPorDefecto() {
        jdbc.update(INSERTAR, "2", "RESERVA", "EXITO");

        assertThat(jdbc.queryForObject("SELECT datos::text FROM evento_auditoria", String.class)).isEqualTo("{}");
    }

    @Test
    @DisplayName("Existen los cinco índices de consulta")
    void indices() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'evento_auditoria'", String.class);

        assertThat(indices).contains("ix_evento_auditoria_fecha", "ix_evento_auditoria_usuario_fecha",
                "ix_evento_auditoria_accion_fecha", "ix_evento_auditoria_entidad_fecha",
                "ix_evento_auditoria_correlacion");
    }

    @ParameterizedTest(name = "{0} por id")
    @ValueSource(strings = {
            "UPDATE evento_auditoria SET resultado = 'FALLO' WHERE id = (SELECT min(id) FROM evento_auditoria)",
            "DELETE FROM evento_auditoria WHERE id = (SELECT min(id) FROM evento_auditoria)",
            "UPDATE evento_auditoria SET resultado = 'FALLO'",
            "DELETE FROM evento_auditoria"})
    @DisplayName("UPDATE y DELETE, por id o masivos, fallan y la fila queda intacta")
    void inmutable(String sentencia) {
        jdbc.update(INSERTAR, "2", "RESERVA", "EXITO");

        assertThatThrownBy(() -> jdbc.execute(sentencia)).isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE resultado = 'EXITO'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("TRUNCATE sigue permitido para las pruebas")
    void truncatePermitido() {
        jdbc.update(INSERTAR, "2", "RESERVA", "EXITO");

        jdbc.execute("TRUNCATE evento_auditoria");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria", Integer.class)).isZero();
    }
}
