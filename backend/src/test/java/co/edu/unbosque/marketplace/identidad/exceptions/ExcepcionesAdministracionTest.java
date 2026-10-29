package co.edu.unbosque.marketplace.identidad.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExcepcionesAdministracionTest {

    @Test
    @DisplayName("El usuario inexistente responde 404 sin propiedades extra")
    void usuarioNoEncontradoResponde404() {
        UsuarioNoEncontradoException error = new UsuarioNoEncontradoException();

        assertThat(error).isInstanceOf(NegocioException.class);
        assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("No encontramos ese usuario.");
        assertThat(error.getPropiedades()).isEmpty();
    }

    @Test
    @DisplayName("La operación no permitida responde 409 con el código y el detalle recibido")
    void operacionNoPermitidaResponde409ConCodigo() {
        OperacionCuentaNoPermitidaException error = new OperacionCuentaNoPermitidaException("No puedes restringir tu propia cuenta.");

        assertThat(error).isInstanceOf(NegocioException.class);
        assertThat(error.getEstado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getMessage()).isEqualTo("No puedes restringir tu propia cuenta.");
        assertThat(error.getPropiedades()).containsOnlyKeys("codigo")
                .containsEntry("codigo", "OPERACION_CUENTA_NO_PERMITIDA");
    }
}
