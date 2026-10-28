package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.auditoria.infraestructura.CapturaAuditoria;
import co.edu.unbosque.marketplace.config.ConfiguracionJwt;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre el login completo: HTTP → validación → servicio → PostgreSQL → JWT real. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AutenticacionIntegracionTest {

    private static final String RUTA = "/api/v1/auth/login";
    private static final String MOTIVO = "Pagos en disputa";
    private static final String MENSAJE = "Correo o contraseña incorrectos. Revísalos e intenta de nuevo.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private JwtDecoder decodificador;

    @Autowired
    private JwtEncoder cifrador;

    @Autowired
    private ConfiguracionJwt configuracion;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode("Segura#2026"), Rol.HUESPED));
    }

    @Test
    @DisplayName("Un login válido responde 200 con el token, el usuario y sin hash, y el JWT lleva los claims")
    void iniciaSesion() throws Exception {
        String cuerpo = login("ana@correo.com", "Segura#2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.usuario.id").isNumber())
                .andExpect(jsonPath("$.usuario.nombre").value("Ana Gómez"))
                .andExpect(jsonPath("$.usuario.correo").value("ana@correo.com"))
                .andExpect(jsonPath("$.usuario.rol").value("HUESPED"))
                .andExpect(jsonPath("$.usuario.contrasenaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode respuesta = json.readTree(cuerpo);
        Jwt jwt = decodificador.decode(respuesta.get("token").asText());
        assertThat(jwt.getSubject()).isEqualTo(respuesta.at("/usuario/id").asText());
        assertThat(UUID.fromString(jwt.getId())).isNotNull();
        assertThat(jwt.getClaimAsString("rol")).isEqualTo("HUESPED");
        assertThat(jwt.getClaimAsString("iss")).isNotBlank();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse(respuesta.get("expiraEn").asText()));
        assertThat(jwt.getExpiresAt()).isBetween(Instant.now().plus(Duration.ofMinutes(58)),
                Instant.now().plus(Duration.ofMinutes(61)));
        assertThat(configuracion.convertidorJwt().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_HUESPED");
        // El mismo token autentica ante la cadena: una ruta sin permiso da 403, no 401.
        mvc.perform(get("/api/v1/otra").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Contraseña errónea y correo inexistente dan el mismo 401 con el mismo cuerpo y las mismas cabeceras")
    void respuestaGenerica() throws Exception {
        MockHttpServletResponse contrasenaErronea = login("ana@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(MENSAJE))
                .andReturn().getResponse();
        MockHttpServletResponse correoInexistente = login("nadie@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        assertThat(json.readTree(correoInexistente.getContentAsString()))
                .isEqualTo(json.readTree(contrasenaErronea.getContentAsString()));
        assertThat(cabeceras(correoInexistente)).isEqualTo(cabeceras(contrasenaErronea));
    }

    @Test
    @DisplayName("Un request mal formado responde 400")
    void rechazaFormaInvalida() throws Exception {
        // Correo vacío y contraseña en blanco ya se cubren en LoginRequestTest.
        login("no-es-correo", "Segura#2026").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una contraseña corta no es 400 por fortaleza: se procesa y da 401")
    void contrasenaCortaDa401() throws Exception {
        login("ana@correo.com", "abc").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un Bearer vencido en el header no impide iniciar sesión")
    void ignoraBearerVencido() throws Exception {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("1").id(UUID.randomUUID().toString())
                .claim("rol", "HUESPED").issuedAt(ahora.minus(Duration.ofHours(3)))
                .expiresAt(ahora.minus(Duration.ofHours(2))).build();
        String vencido = cifrador.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"ana@correo.com\", \"contrasena\": \"Segura#2026\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una cuenta restringida con la contraseña correcta recibe 403 con el código y el motivo, sin token")
    void rechazaCuentaRestringida(CapturedOutput salida) throws Exception {
        jdbc.update("UPDATE usuario SET estado = 'RESTRINGIDA', motivo_restriccion = ? WHERE correo = ?",
                MOTIVO, "ana@correo.com");

        try (var captura = new CapturaAuditoria()) {
            MockHttpServletResponse respuesta = login("ana@correo.com", "Segura#2026")
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detail").value("Tu cuenta está restringida."))
                    .andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"))
                    .andExpect(jsonPath("$.motivo").value(MOTIVO))
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andReturn().getResponse();

            assertThat(captura.cantidad()).isEqualTo(1);
            assertThat(captura.texto(0)).contains("AUTENTICACION", "FALLO", "cuenta_restringida")
                    .doesNotContain(MOTIVO, "ana@correo.com", "Segura#2026");
            assertThat(salida.getAll()).doesNotContain(MOTIVO, "Segura#2026");
            // Un JWT siempre empieza por el encabezado codificado "eyJ": no debe aparecer en ningún lado.
            assertThat(respuesta.getContentAsString()).doesNotContain("eyJ");
            assertThat(captura.texto(0)).doesNotContain("eyJ");
            assertThat(salida.getAll()).doesNotContain("eyJ");
        }
    }

    @Test
    @DisplayName("Una cuenta desactivada con la contraseña correcta recibe 403 con el código, sin motivo ni token")
    void rechazaCuentaDesactivada() throws Exception {
        jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA' WHERE correo = ?", "ana@correo.com");

        try (var captura = new CapturaAuditoria()) {
            login("ana@correo.com", "Segura#2026")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Tu cuenta está desactivada."))
                    .andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"))
                    .andExpect(jsonPath("$.motivo").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist());

            assertThat(captura.texto(0)).contains("FALLO", "cuenta_desactivada")
                    .doesNotContain("ana@correo.com", "Segura#2026");
        }
    }

    @Test
    @DisplayName("Con la contraseña errónea una cuenta restringida o desactivada da el mismo 401 que un correo inexistente")
    void contrasenaErroneaNoRevelaElEstado() throws Exception {
        MockHttpServletResponse inexistente = login("nadie@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        jdbc.update("UPDATE usuario SET estado = 'RESTRINGIDA', motivo_restriccion = ? WHERE correo = ?",
                MOTIVO, "ana@correo.com");
        MockHttpServletResponse restringida = login("ana@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA', motivo_restriccion = NULL WHERE correo = ?",
                "ana@correo.com");
        MockHttpServletResponse desactivada = login("ana@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        JsonNode esperado = json.readTree(inexistente.getContentAsString());
        assertThat(json.readTree(restringida.getContentAsString())).isEqualTo(esperado);
        assertThat(json.readTree(desactivada.getContentAsString())).isEqualTo(esperado);
        assertThat(esperado.has("codigo")).isFalse();
        assertThat(esperado.has("motivo")).isFalse();
        assertThat(cabeceras(restringida)).isEqualTo(cabeceras(inexistente));
        assertThat(cabeceras(desactivada)).isEqualTo(cabeceras(inexistente));
    }

    // Se excluyen las cabeceras que cambian legítimamente en cada request (identificador de correlación y fecha).
    private static Map<String, List<String>> cabeceras(MockHttpServletResponse respuesta) {
        Map<String, List<String>> cabeceras = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        respuesta.getHeaderNames().stream()
                .filter(nombre -> !nombre.equalsIgnoreCase("X-Correlation-Id")
                        && !nombre.equalsIgnoreCase(HttpHeaders.DATE))
                .forEach(nombre -> cabeceras.put(nombre, respuesta.getHeaders(nombre)));
        return cabeceras;
    }

    private ResultActions login(String correo, String contrasena) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, contrasena);
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
