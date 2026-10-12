package co.edu.unbosque.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstadoControllerTest {

    private final EstadoController controlador = new EstadoController();

    @Test
    @DisplayName("El punto de estado identifica la aplicación y la reporta activa")
    void reportaLaAplicacionActiva() {
        var respuesta = controlador.estado();

        assertThat(respuesta)
                .containsEntry("aplicacion", "marketplace-app")
                .containsEntry("estado", "activa");
    }
}
