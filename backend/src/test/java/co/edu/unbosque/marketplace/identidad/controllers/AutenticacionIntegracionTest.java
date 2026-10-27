package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.config.ConfiguracionJwt;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre el login completo: HTTP → validación → servicio → PostgreSQL → JWT real. */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionIntegracionTest {

    private static final String RUTA = "/api/v1/auth/login";
    private static final String MENSAJE = "Correo o contraseña incorrectos. Revísalos e intenta de nuevo.";

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder codificador;
    @Autowired private JwtDecoder decodificador;
    @Autowired private JwtEncoder cifrador;
    @Autowired private ConfiguracionJwt configuracion;
    @Autowired private ObjectMapper json;

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
    @DisplayName("Contraseña errónea y correo inexistente dan el mismo 401 con el mismo cuerpo")
    void respuestaGenerica() throws Exception {
        String contrasenaErronea = login("ana@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(MENSAJE))
                .andReturn().getResponse().getContentAsString();
        String correoInexistente = login("nadie@correo.com", "Otra#Clave1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(correoInexistente)).isEqualTo(json.readTree(contrasenaErronea));
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

    private ResultActions login(String correo, String contrasena) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, contrasena);
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
