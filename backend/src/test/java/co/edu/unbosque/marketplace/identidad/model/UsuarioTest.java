package co.edu.unbosque.marketplace.identidad.model;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.identidad.UsuariosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    @DisplayName("Una cuenta activa puede iniciar sesión")
    void cuentaActivaPuedeIniciarSesion() {
        Usuario usuario = Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash", Rol.HUESPED);

        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(usuario.puedeIniciarSesion()).isTrue();
        assertThat(usuario.getMotivoRestriccion()).isNull();
    }

    @Test
    @DisplayName("Una cuenta desactivada no puede iniciar sesión")
    void cuentaDesactivadaNoPuedeIniciarSesion() {
        assertThat(UsuariosDePrueba.desactivada(7L).puedeIniciarSesion()).isFalse();
    }

    @Test
    @DisplayName("Una cuenta restringida no puede iniciar sesión y conserva su motivo")
    void cuentaRestringidaNoPuedeIniciarSesion() {
        Usuario usuario = UsuariosDePrueba.restringida(7L, "Pagos en disputa");

        assertThat(usuario.puedeIniciarSesion()).isFalse();
        assertThat(usuario.getMotivoRestriccion()).isEqualTo("Pagos en disputa");
    }
}
