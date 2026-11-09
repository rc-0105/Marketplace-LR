package co.edu.unbosque.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstadoControllerTest {

    private final EstadoController controlador = new EstadoController("4f2c9a1");

    @Test
    @DisplayName("El punto de estado identifica la aplicación, la reporta activa y dice de qué commit salió")
    void reportaLaAplicacionActiva() {
        var respuesta = controlador.estado();

        assertThat(respuesta)
                .containsEntry("aplicacion", "marketplace-app")
                .containsEntry("estado", "activa")
                .containsEntry("version", "4f2c9a1");
    }
}
