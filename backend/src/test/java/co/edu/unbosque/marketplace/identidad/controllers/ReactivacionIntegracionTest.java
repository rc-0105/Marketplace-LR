package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.auditoria.infraestructura.CapturaAuditoria;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cableado HTTP y de seguridad de la reactivación; las reglas de negocio se prueban en los unitarios. */
@SpringBootTest
@AutoConfigureMockMvc
class ReactivacionIntegracionTest {

    private static final String RUTA = "/api/v1/perfil/reactivar";
    private static final String CONTRASENA = "Segura#2026";
    private static final String MOTIVO = "Pagos en disputa";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private JwtEncoder cifrador;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    @Test
    @DisplayName("Una cuenta desactivada con sus credenciales responde 200 con una sesión usable y queda activa")
    void reactivaCuentaDesactivada() throws Exception {
        cambiarEstado("DESACTIVADA", null);

        try (var captura = new CapturaAuditoria()) {
            String cuerpo = reactivar("ana@correo.com", CONTRASENA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tipo").value("Bearer"))
                    .andExpect(jsonPath("$.usuario.estado").value("ACTIVA"))
                    .andReturn().getResponse().getContentAsString();
            String token = json.readTree(cuerpo).get("token").asText();

            assertThat(estado()).isEqualTo("ACTIVA");
            mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());
            assertThat(captura.texto(0)).contains("AUTENTICACION", "reactivar_cuenta", "EXITO")
                    .doesNotContain(CONTRASENA, token, "ana@correo.com");
        }
    }

    @Test
    @DisplayName("Una cuenta restringida recibe 403 con el código y el motivo, sin token y sin cambios")
    void noReactivaCuentaRestringida() throws Exception {
        cambiarEstado("RESTRINGIDA", MOTIVO);

        reactivar("ana@correo.com", CONTRASENA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"))
                .andExpect(jsonPath("$.motivo").value(MOTIVO))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(estado()).isEqualTo("RESTRINGIDA");
    }

    @Test
    @DisplayName("Una cuenta ya activa recibe 409 ProblemDetail con el código y sin token")
    void rechazaCuentaActiva() throws Exception {
        reactivar("ana@correo.com", CONTRASENA)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CUENTA_ACTIVA"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Credenciales malas dan el mismo 401 que el login, sin revelar el estado de la cuenta")
    void credencialesMalasDanElMismo401DelLogin() throws Exception {
        cambiarEstado("DESACTIVADA", null);
        MockHttpServletResponse login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("nadie@correo.com", "Otra#Clave1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        MockHttpServletResponse inexistente = reactivar("nadie@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        MockHttpServletResponse contrasenaMala = reactivar("ana@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        // Solo difiere `instance`, que es la ruta pedida.
        assertThat(sinInstancia(inexistente)).isEqualTo(sinInstancia(login));
        assertThat(sinInstancia(contrasenaMala)).isEqualTo(sinInstancia(login));
        assertThat(estado()).isEqualTo("DESACTIVADA");
    }

    @Test
    @DisplayName("Un Bearer vencido en el header se ignora y no impide reactivar")
    void ignoraBearerVencido() throws Exception {
        cambiarEstado("DESACTIVADA", null);
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("1").id(UUID.randomUUID().toString())
                .claim("rol", "HUESPED").issuedAt(ahora.minus(Duration.ofHours(3)))
                .expiresAt(ahora.minus(Duration.ofHours(2))).build();
        String vencido = cifrador.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("ana@correo.com", CONTRASENA)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un correo mal formado o una contraseña en blanco dan 400")
    void rechazaFormaInvalida() throws Exception {
        reactivar("no-es-correo", CONTRASENA).andExpect(status().isBadRequest());
        reactivar("ana@correo.com", "   ").andExpect(status().isBadRequest());
    }

    private JsonNode sinInstancia(MockHttpServletResponse respuesta) throws Exception {
        return ((ObjectNode) json.readTree(respuesta.getContentAsString())).without("instance");
    }

    private ResultActions reactivar(String correo, String contrasena) throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo(correo, contrasena)));
    }

    private static String cuerpo(String correo, String contrasena) {
        return """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, contrasena);
    }

    private void cambiarEstado(String estado, String motivo) {
        jdbc.update("UPDATE usuario SET estado = ?, motivo_restriccion = ? WHERE correo = ?",
                estado, motivo, "ana@correo.com");
    }

    private String estado() {
        return jdbc.queryForObject("SELECT estado FROM usuario WHERE correo = ?", String.class, "ana@correo.com");
    }
}
