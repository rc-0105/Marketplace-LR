package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.SesionInvalidada;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.SesionInvalidadaRepository;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Flujo completo de RF-03: login real, logout, y el mismo token rechazado después (CA3). */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutIntegracionTest {

    private static final String RUTA_LOGOUT = "/api/v1/auth/logout";
    private static final String CONTRASENA = "Segura#2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private SesionInvalidadaRepository sesiones;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Luis Pérez", "luis@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    @Test
    @DisplayName("El logout responde 204, guarda el jti con el exp del token y el mismo token deja de valer")
    void cierraSesionYRevocaElToken() throws Exception {
        JsonNode sesion = iniciarSesion("ana@correo.com");
        String token = sesion.get("token").asText();

        logout(token).andExpect(status().isNoContent());

        List<SesionInvalidada> filas = sesiones.findAll();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getUsuarioId()).isEqualTo(sesion.at("/usuario/id").asLong());
        assertThat(filas.get(0).getExpiraEn()).isEqualTo(Instant.parse(sesion.get("expiraEn").asText()));
        // Un segundo logout y cualquier endpoint protegido dan 401: el decoder ya no acepta el token.
        logout(token).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/otra").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(sesiones.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cerrar la sesión de un usuario no afecta el token de otro")
    void otroUsuarioNoSeAfecta() throws Exception {
        String tokenAna = iniciarSesion("ana@correo.com").get("token").asText();
        String tokenLuis = iniciarSesion("luis@correo.com").get("token").asText();

        logout(tokenAna).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/otra").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenLuis))
                .andExpect(status().isForbidden());
        logout(tokenLuis).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("El logout sin Authorization responde 401 ProblemDetail")
    void sinTokenResponde401() throws Exception {
        mvc.perform(post(RUTA_LOGOUT))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private JsonNode iniciarSesion(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    private ResultActions logout(String token) throws Exception {
        return mvc.perform(post(RUTA_LOGOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
