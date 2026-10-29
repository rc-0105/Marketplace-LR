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
        Usuario desactivada = UsuariosDePrueba.desactivada(8L);

        assertThatThrownBy(() -> restringida.restringir("Otro")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> desactivada.restringir("Otro")).isInstanceOf(IllegalStateException.class);
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
        Usuario activa = UsuariosDePrueba.activa(7L);
        Usuario desactivada = UsuariosDePrueba.desactivada(8L);

        assertThatThrownBy(activa::levantarRestriccion).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(desactivada::levantarRestriccion).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Reactivar una cuenta desactivada la deja activa")
    void reactivaCuentaDesactivada() {
        Usuario usuario = UsuariosDePrueba.desactivada(7L);

        usuario.reactivar();

        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(usuario.puedeIniciarSesion()).isTrue();
    }

    @Test
    @DisplayName("Una cuenta activa o restringida no se reactiva y conserva su estado")
    void noReactivaCuentaQueNoEstaDesactivada() {
        Usuario activa = UsuariosDePrueba.activa(7L);
        Usuario restringida = UsuariosDePrueba.restringida(8L, "Pagos en disputa");

        assertThatThrownBy(activa::reactivar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(restringida::reactivar).isInstanceOf(IllegalStateException.class);

        assertThat(activa.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(restringida.getEstado()).isEqualTo(EstadoCuenta.RESTRINGIDA);
        assertThat(restringida.getMotivoRestriccion()).isEqualTo("Pagos en disputa");
    }
}
