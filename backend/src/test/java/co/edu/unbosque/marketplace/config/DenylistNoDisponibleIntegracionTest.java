package co.edu.unbosque.marketplace.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.compartido.infraestructura.web.FiltroCorrelacion;
import co.edu.unbosque.marketplace.identidad.services.SesionService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/** Con la denylist caída el token es válido pero no verificable: 503, no 401 (S-A18). */
@SpringBootTest
@AutoConfigureMockMvc
class DenylistNoDisponibleIntegracionTest {

    private static final String RUTA_PROTEGIDA = "/api/v1/otra";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder codificador;

    @Autowired
    private PropiedadesJwt propiedades;

    @MockBean
    private SesionService sesiones;

    @Test
    @DisplayName("Si la denylist falla responde 503 ProblemDetail genérico y, al recuperarse, el mismo token se procesa")
    void responde503YSeRecupera() throws Exception {
        String token = token(Duration.ofMinutes(5));
        when(sesiones.estaInvalidada(anyString())).thenThrow(new DataAccessResourceFailureException("BD caída"));

        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "El servicio no está disponible en este momento. Intenta de nuevo en unos minutos."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(header().exists(FiltroCorrelacion.CABECERA));

        // doReturn: when() invocaría el stub que lanza.
        doReturn(false).when(sesiones).estaInvalidada(anyString());

        // Sin permiso para la ruta pero autenticado: 403 prueba que el token ya no falla.
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token vencido durante la caída responde 401 sin consultar la denylist")
    void vencidoDa401SinConsultar() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(Duration.ofHours(-1))))
                .andExpect(status().isUnauthorized());

        verify(sesiones, never()).estaInvalidada(anyString());
    }

    private String token(Duration vigencia) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject("1")
                .id(UUID.randomUUID().toString())
                .claim("rol", "HUESPED")
                .issuedAt(ahora.minus(Duration.ofHours(2)))
                .expiresAt(ahora.plus(vigencia))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }
}
