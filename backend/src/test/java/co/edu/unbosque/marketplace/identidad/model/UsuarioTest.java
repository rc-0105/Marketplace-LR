package co.edu.unbosque.marketplace.identidad.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    @DisplayName("Una cuenta activa puede iniciar sesión")
    void cuentaActivaPuedeIniciarSesion() {
        Usuario usuario = Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash", Rol.HUESPED);

        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(usuario.puedeIniciarSesion()).isTrue();
    }
}
