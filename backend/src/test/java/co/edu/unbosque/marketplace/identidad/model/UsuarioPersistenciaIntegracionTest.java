package co.edu.unbosque.marketplace.identidad.model;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.identidad.UsuariosDePrueba;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Comprueba que el estado y el motivo de restricción se guardan y se leen de PostgreSQL. */
@SpringBootTest
class UsuarioPersistenciaIntegracionTest {

    @Autowired
    private UsuarioRepository usuarios;

    @BeforeEach
    void limpiar() {
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("Una cuenta restringida se guarda con su motivo y se vuelve a leer igual")
    void guardaYLeeCuentaRestringida() {
        Usuario guardado = usuarios.save(UsuariosDePrueba.restringida(null, "Pagos en disputa"));

        Usuario leido = usuarios.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getEstado()).isEqualTo(EstadoCuenta.RESTRINGIDA);
        assertThat(leido.getMotivoRestriccion()).isEqualTo("Pagos en disputa");
    }

    @Test
    @DisplayName("Una cuenta desactivada se guarda sin motivo")
    void guardaYLeeCuentaDesactivada() {
        Usuario guardado = usuarios.save(UsuariosDePrueba.desactivada(null));

        Usuario leido = usuarios.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
        assertThat(leido.getMotivoRestriccion()).isNull();
    }
}
