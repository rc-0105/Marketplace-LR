package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ManejadorFallosSeguridadTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final ManejadorFallosSeguridad manejador = new ManejadorFallosSeguridad(mapper);
    private final MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/otra");
    private final MockHttpServletResponse respuesta = new MockHttpServletResponse();

    @Test
    @DisplayName("Sin autenticar responde 401 como ProblemDetail con WWW-Authenticate")
    void responde401() throws Exception {
        manejador.commence(peticion, respuesta, new BadCredentialsException("token vencido"));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        JsonNode cuerpo = mapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("detail").asText()).isEqualTo("Necesitas iniciar sesión para continuar.");
        assertThat(cuerpo.get("instance").asText()).isEqualTo("/api/v1/otra");
        assertThat(cuerpo.has("trace")).isFalse();
        assertThat(respuesta.getContentAsString()).doesNotContain("token vencido");
    }

    @Test
    @DisplayName("Sin permiso responde 403 como ProblemDetail")
    void responde403() throws Exception {
        manejador.handle(peticion, respuesta, new AccessDeniedException("denegado"));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode cuerpo = mapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("detail").asText()).isEqualTo("No tienes permiso para realizar esta acción.");
        assertThat(cuerpo.has("trace")).isFalse();
    }

    @Test
    @DisplayName("Si la causa es SesionNoVerificableException responde 503 genérico, sin WWW-Authenticate ni trace")
    void responde503() throws Exception {
        AuthenticationServiceException error = new AuthenticationServiceException("interno",
                new SesionNoVerificableException(new RuntimeException("BD caída")));

        manejador.onAuthenticationFailure(peticion, respuesta, error);

        assertThat(respuesta.getStatus()).isEqualTo(503);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isNull();
        JsonNode cuerpo = mapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("detail").asText())
                .isEqualTo("El servicio no está disponible en este momento. Intenta de nuevo en unos minutos.");
        assertThat(cuerpo.has("trace")).isFalse();
        assertThat(respuesta.getContentAsString()).doesNotContain("BD caída", "interno");
    }

    @Test
    @DisplayName("Cualquier otro fallo de autenticación responde 401, incluso un AuthenticationServiceException")
    void respondeOtrosFallos401() throws Exception {
        manejador.onAuthenticationFailure(peticion, respuesta,
                new AuthenticationServiceException("otro", new IllegalStateException()));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }
}
