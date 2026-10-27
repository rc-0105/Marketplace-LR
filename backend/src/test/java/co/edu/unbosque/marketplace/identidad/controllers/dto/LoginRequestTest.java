package co.edu.unbosque.marketplace.identidad.controllers.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRequestTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    @Test
    @DisplayName("Una contraseña corta como abc no se rechaza: el login no valida fortaleza")
    void noValidaFortaleza() {
        assertThat(campos("ana@correo.com", "abc")).isEmpty();
    }

    @Test
    @DisplayName("Rechaza el correo vacío, la contraseña en blanco y el correo mal formado")
    void rechazaFormaInvalida() {
        assertThat(campos("", "abc")).contains("correo");
        assertThat(campos("ana@correo.com", "   ")).containsExactly("contrasena");
        assertThat(campos("no-es-correo", "abc")).containsExactly("correo");
    }

    @Test
    @DisplayName("Al imprimir la solicitud no aparecen el correo ni la contraseña")
    void ocultaDatosAlImprimir() {
        assertThat(new LoginRequest("ana@correo.com", "Segura#2026").toString())
                .doesNotContain("ana@correo.com", "Segura#2026");
    }

    private List<String> campos(String correo, String contrasena) {
        return validador.validate(new LoginRequest(correo, contrasena)).stream()
                .map(violacion -> violacion.getPropertyPath().toString()).toList();
    }
}
