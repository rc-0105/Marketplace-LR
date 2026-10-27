package co.edu.unbosque.marketplace.identidad.controllers.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistrarUsuarioRequestTest {

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
    @DisplayName("Una solicitud completa y con contraseña válida no tiene errores")
    void aceptaSolicitudValida() {
        assertThat(validar("Segura#2026")).isEmpty();
    }

    @ParameterizedTest(name = "contraseña \"{0}\"")
    @ValueSource(strings = {"Corta#1", "sinmayuscula#1", "SinEspecial123"})
    @DisplayName("Rechaza contraseñas cortas, sin mayúscula o sin carácter especial")
    void rechazaContrasenaDebil(String contrasena) {
        assertThat(validar(contrasena))
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsOnly("contrasena");
    }

    @Test
    @DisplayName("Rechaza un correo con formato inválido")
    void rechazaCorreoInvalido() {
        var solicitud = new RegistrarUsuarioRequest("Ana", "no-es-correo", "Segura#2026", "HUESPED");

        assertThat(validador.validate(solicitud))
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("correo");
    }

    @Test
    @DisplayName("Al imprimir la solicitud nunca aparece la contraseña")
    void ocultaLaContrasenaAlImprimir() {
        var solicitud = new RegistrarUsuarioRequest("Ana", "ana@correo.com", "Segura#2026", "HUESPED");

        assertThat(solicitud.toString())
                .contains("ana@correo.com")
                .doesNotContain("Segura#2026");
    }

    private Set<ConstraintViolation<RegistrarUsuarioRequest>> validar(String contrasena) {
        return validador.validate(new RegistrarUsuarioRequest("Ana Gómez", "ana@correo.com", contrasena, "HUESPED"));
    }
}
