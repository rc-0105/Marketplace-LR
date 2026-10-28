package co.edu.unbosque.marketplace.identidad.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExcepcionesCuentaTest {

    private static final String CODIGO = "codigo";

    @Test
    @DisplayName("La cuenta restringida responde 403 con el código y el motivo")
    void cuentaRestringidaLlevaCodigoYMotivo() {
        CuentaRestringidaException error = new CuentaRestringidaException("Pagos en disputa");

        assertThat(error).isInstanceOf(NegocioException.class);
        assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(error.getMessage()).isEqualTo("Tu cuenta está restringida.");
        assertThat(error.getPropiedades())
                .containsEntry(CODIGO, "CUENTA_RESTRINGIDA")
                .containsEntry("motivo", "Pagos en disputa")
                .hasSize(2);
    }

    @Test
    @DisplayName("La cuenta restringida no se construye sin motivo")
    void cuentaRestringidaExigeMotivo() {
        assertThatThrownBy(() -> new CuentaRestringidaException(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("La cuenta desactivada responde 403 con el código y sin motivo")
    void cuentaDesactivadaSoloLlevaCodigo() {
        CuentaDesactivadaException error = new CuentaDesactivadaException();

        assertThat(error).isInstanceOf(NegocioException.class);
        assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(error.getMessage()).isEqualTo("Tu cuenta está desactivada.");
        assertThat(error.getPropiedades()).containsOnlyKeys(CODIGO).containsEntry(CODIGO, "CUENTA_DESACTIVADA");
    }

    @Test
    @DisplayName("La confirmación inválida responde 403 con el código y ninguna otra propiedad")
    void confirmacionInvalidaSoloLlevaCodigo() {
        ConfirmacionInvalidaException error = new ConfirmacionInvalidaException();

        assertThat(error).isInstanceOf(NegocioException.class);
        assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(error.getMessage()).isEqualTo("La contraseña no es correcta.");
        assertThat(error.getPropiedades()).containsOnlyKeys(CODIGO).containsEntry(CODIGO, "CONFIRMACION_INVALIDA");
    }

    @Test
    @DisplayName("Las reservas futuras responden 409 con el código, el conteo numérico y el número en el detalle")
    void reservasFuturasLlevaConteo() {
        ReservasFuturasException error = new ReservasFuturasException(3L);

        assertThat(error).isInstanceOf(NegocioException.class);
        assertThat(error.getEstado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getMessage()).isEqualTo("Tienes reservas confirmadas pendientes (3). "
                + "Cancélalas o espera a que terminen antes de continuar.");
        assertThat(error.getPropiedades())
                .containsOnlyKeys(CODIGO, "reservasBloqueantes")
                .containsEntry(CODIGO, "RESERVAS_FUTURAS_ACTIVAS")
                .containsEntry("reservasBloqueantes", 3L);
    }

    @Test
    @DisplayName("El detalle de las reservas futuras trae el número que se le pasó")
    void reservasFuturasUsaElNumeroRecibido() {
        assertThat(new ReservasFuturasException(12L).getMessage()).contains("(12)");
    }
}
