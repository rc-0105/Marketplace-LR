package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static co.edu.unbosque.marketplace.auditoria.infraestructura.SanitizadorDatos.OCULTO;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanitizadorDatosTest {

    @Test
    @DisplayName("Oculta contraseñas, tokens y datos de pago sin importar mayúsculas")
    void ocultaDatosSensibles() {
        var saneados = SanitizadorDatos.sanear(Map.of(
                "Contrasena", "secreta123",
                "accessToken", "eyJhbGciOi",
                "numeroTarjeta", "4111111111111111",
                "correo", "ana@correo.com"));

        assertThat(saneados)
                .containsEntry("Contrasena", OCULTO)
                .containsEntry("accessToken", OCULTO)
                .containsEntry("numeroTarjeta", OCULTO)
                .containsEntry("correo", "ana@correo.com");
    }

    @Test
    @DisplayName("Revisa también los datos anidados")
    void saneaDatosAnidados() {
        var saneados = SanitizadorDatos.sanear(Map.of(
                "detalle", Map.of("cvv", "123", "noches", 3)));

        assertThat(saneados.get("detalle")).isEqualTo(Map.of("cvv", OCULTO, "noches", 3));
    }
}
