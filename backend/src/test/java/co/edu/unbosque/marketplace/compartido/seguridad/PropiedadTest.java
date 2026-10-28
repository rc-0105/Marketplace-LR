package co.edu.unbosque.marketplace.compartido.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PropiedadTest {

    @Test
    @DisplayName("El dueño del recurso puede modificarlo")
    void elDuenoPasa() {
        assertThatCode(() -> Propiedad.exigirPropietario(7L, 7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Otro usuario no puede modificar un recurso ajeno")
    void otroUsuarioEsRechazado() {
        assertThatThrownBy(() -> Propiedad.exigirPropietario(7L, 8L))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessage("No tienes permiso para realizar esta acción.")
                .extracting("estado").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Un recurso sin dueño conocido se niega en vez de quedar abierto")
    void sinDuenoSeNiega() {
        assertThatThrownBy(() -> Propiedad.exigirPropietario(null, 8L))
                .isInstanceOf(AccesoDenegadoException.class);
    }
}
