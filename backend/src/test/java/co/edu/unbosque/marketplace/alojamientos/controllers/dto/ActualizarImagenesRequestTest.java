package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActualizarImagenesRequestTest {

    private static final String UUID_VALIDO = "0b9d7c1e-4f55-4c2e-9a3b-6d1f2a8e5c70";
    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean esValida(String... imagenes) {
        return validador.validate(new ActualizarImagenesRequest(List.of(imagenes))).isEmpty();
    }

    @Test
    @DisplayName("Acepta URL http y https, y la ruta de una imagen subida")
    void aceptaLoQueDebe() {
        assertThat(esValida("https://imagenes.com/1.jpg", "http://imagenes.com/2.png")).isTrue();
        assertThat(esValida("/api/v1/imagenes/" + UUID_VALIDO)).isTrue();
    }

    @Test
    @DisplayName("Rechaza otros esquemas, texto libre y rutas relativas ajenas a las imágenes subidas")
    void rechazaLoDemas() {
        assertThat(esValida("ftp://imagenes.com/1.jpg")).isFalse();
        assertThat(esValida("javascript:alert(1)")).isFalse();
        assertThat(esValida("no-es-una-url")).isFalse();
        assertThat(esValida("/api/v1/usuarios")).isFalse();
        assertThat(esValida("/api/v1/imagenes/../auth/login")).isFalse();
        assertThat(esValida("/api/v1/imagenes/no-es-uuid")).isFalse();
        assertThat(esValida("/api/v1/imagenes/" + UUID_VALIDO + "/extra")).isFalse();
    }

    @Test
    @DisplayName("Una imagen en blanco sigue rechazándose con su propio mensaje")
    void rechazaEnBlanco() {
        assertThat(esValida(" ")).isFalse();
    }
}
