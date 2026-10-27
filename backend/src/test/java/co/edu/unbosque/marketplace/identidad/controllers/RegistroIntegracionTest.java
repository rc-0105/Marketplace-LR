package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre el registro completo: HTTP → validación → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class RegistroIntegracionTest {

    private static final String RUTA = "/api/v1/auth/registro";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @BeforeEach
    void limpiar() {
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("Registra al huésped con la cuenta activa y no devuelve la contraseña")
    void registraHuesped() throws Exception {
        registrar("ana@correo.com", "Segura#2026", "HUESPED")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.correo").value("ana@correo.com"))
                .andExpect(jsonPath("$.rol").value("HUESPED"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());

        Usuario guardado = usuarios.findAll().get(0);
        assertThat(guardado.getContrasenaHash()).isNotEqualTo("Segura#2026");
        assertThat(codificador.matches("Segura#2026", guardado.getContrasenaHash())).isTrue();
    }

    @Test
    @DisplayName("Si el correo ya está registrado responde 409 y no crea otra cuenta")
    void rechazaCorreoEnUso() throws Exception {
        registrar("ana@correo.com", "Segura#2026", "HUESPED").andExpect(status().isCreated());

        registrar("ANA@correo.com", "Otra#Clave1", "ANFITRION")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El correo ya está en uso."));

        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si la petición pide el rol administrador responde 400")
    void rechazaRolAdministrador() throws Exception {
        registrar("ana@correo.com", "Segura#2026", "ADMINISTRADOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El rol debe ser HUESPED o ANFITRION."));

        assertThat(usuarios.count()).isZero();
    }

    @Test
    @DisplayName("Si la petición envía un rol inexistente responde 400")
    void rechazaRolInexistente() throws Exception {
        registrar("ana@correo.com", "Segura#2026", "SUPERADMIN").andExpect(status().isBadRequest());

        assertThat(usuarios.count()).isZero();
    }

    @Test
    @DisplayName("Si la contraseña no cumple la regla responde 400 indicando el campo")
    void rechazaContrasenaDebil() throws Exception {
        registrar("ana@correo.com", "debil", "HUESPED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").exists());

        assertThat(usuarios.count()).isZero();
    }

    @Test
    @DisplayName("Si el cuerpo no es JSON válido responde 400")
    void rechazaCuerpoInvalido() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{nombre"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions registrar(String correo, String contrasena, String rol) throws Exception {
        String cuerpo = """
                {"nombre": "Ana Gómez", "correo": "%s", "contrasena": "%s", "rol": "%s"}
                """.formatted(correo, contrasena, rol);
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
