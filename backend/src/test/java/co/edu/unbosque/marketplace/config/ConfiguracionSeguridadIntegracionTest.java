package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.compartido.infraestructura.web.FiltroCorrelacion;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/** Cadena de seguridad real, con tokens reales firmados por el encoder (no jwt()). */
@SpringBootTest
@AutoConfigureMockMvc
class ConfiguracionSeguridadIntegracionTest {

    private static final String RUTA_PROTEGIDA = "/api/v1/otra";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder codificador;

    @Autowired
    private PropiedadesJwt propiedades;

    @Autowired
    private UsuarioRepository usuarios;

    @AfterEach
    void limpiar() {
        usuarios.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/estado", "/actuator/health", "/actuator/info", "/v3/api-docs",
            "/swagger-ui/index.html"})
    @DisplayName("Las rutas públicas no exigen token")
    void rutasPublicas(String ruta) throws Exception {
        int estado = mvc.perform(get(ruta)).andReturn().getResponse().getStatus();

        assertThat(estado).isNotIn(401, 403);
    }

    @Test
    @DisplayName("Una ruta no declarada sin token responde 401 ProblemDetail")
    void sinTokenResponde401() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar."))
                .andExpect(header().exists(FiltroCorrelacion.CABECERA));
    }

    @Test
    @DisplayName("Una ruta no declarada con token válido responde 403 ProblemDetail")
    void conTokenResponde403() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, bearer(token(Duration.ofMinutes(5)))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para realizar esta acción."))
                .andExpect(header().exists(FiltroCorrelacion.CABECERA));
    }

    @Test
    @DisplayName("Un token vencido responde 401")
    void tokenVencido() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, bearer(token(Duration.ofHours(-1)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar."));
    }

    @Test
    @DisplayName("Un token con la firma alterada responde 401")
    void firmaAlterada() throws Exception {
        String[] partes = token(Duration.ofMinutes(5)).split("[.]");
        String alterado = partes[0] + "." + partes[1] + "." + (partes[2].charAt(0) == 'A' ? 'B' : 'A')
                + partes[2].substring(1);

        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, bearer(alterado)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Registro y login ignoran un Bearer vencido en vez de dar 401")
    void bearerVencidoNoAfectaRutasDeAcceso() throws Exception {
        String vencido = bearer(token(Duration.ofHours(-1)));
        String cuerpo = """
                {"nombre": "Ana Gómez", "correo": "ana@correo.com", "contrasena": "Segura#2026", "rol": "HUESPED"}
                """;

        mvc.perform(post("/api/v1/auth/registro").header(HttpHeaders.AUTHORIZATION, vencido)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        int estadoLogin = mvc.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, vencido)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(estadoLogin).isNotIn(401, 403);
    }

    private String token(Duration vigencia) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(vigencia);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject("1")
                .id(UUID.randomUUID().toString())
                .claim("rol", "HUESPED")
                .issuedAt(ahora.minus(Duration.ofHours(2)))
                .expiresAt(expira)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
