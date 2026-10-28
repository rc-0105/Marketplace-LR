package co.edu.unbosque.marketplace.identidad.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("Restringir una cuenta activa la deja restringida con el motivo sin espacios sobrantes")
    void restringeCuentaActiva() {
        Usuario usuario = UsuariosDePrueba.activa(7L);

        usuario.restringir("  Pagos en disputa  ");

        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.RESTRINGIDA);
        assertThat(usuario.getMotivoRestriccion()).isEqualTo("Pagos en disputa");
    }

    @Test
    @DisplayName("Solo una cuenta activa se puede restringir")
    void noRestringeCuentaQueNoEstaActiva() {
        Usuario restringida = UsuariosDePrueba.restringida(7L, "Motivo previo");

        assertThatThrownBy(() -> restringida.restringir("Otro")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> UsuariosDePrueba.desactivada(8L).restringir("Otro"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(restringida.getMotivoRestriccion()).isEqualTo("Motivo previo");
    }

    @Test
    @DisplayName("Restringir exige un motivo con texto")
    void restringirExigeMotivo() {
        Usuario usuario = UsuariosDePrueba.activa(7L);

        assertThatThrownBy(() -> usuario.restringir(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usuario.restringir("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    @DisplayName("Levantar la restricción deja la cuenta activa y sin motivo")
    void levantaRestriccion() {
        Usuario usuario = UsuariosDePrueba.restringida(7L, "Pagos en disputa");

        usuario.levantarRestriccion();

        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(usuario.getMotivoRestriccion()).isNull();
    }

    @Test
    @DisplayName("Solo una cuenta restringida se puede liberar")
    void noLevantaRestriccionDeCuentaNoRestringida() {
        assertThatThrownBy(() -> UsuariosDePrueba.activa(7L).levantarRestriccion())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> UsuariosDePrueba.desactivada(8L).levantarRestriccion())
                .isInstanceOf(IllegalStateException.class);
    }
}
