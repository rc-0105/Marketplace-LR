package co.edu.unbosque.marketplace.identidad.controllers.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmacionCuentaRequestTest {

    @Test
    @DisplayName("Al imprimir la solicitud no aparece la contraseña")
    void ocultaLaContrasenaAlImprimir() {
        assertThat(new ConfirmacionCuentaRequest("Segura#2026")).hasToString("ConfirmacionCuentaRequest[]");
    }
}
