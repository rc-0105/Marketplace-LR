package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.identidad.services.SesionService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class ConfiguracionJwtTest {

    private static final String EMISOR = "marketplace-app";

    private final ConfiguracionJwt configuracion = new ConfiguracionJwt();
    private final PropiedadesJwt propiedades = new PropiedadesJwt("S".repeat(32), 60, EMISOR);
    private final SecretKey clave = configuracion.claveJwt(propiedades);
    private final JwtEncoder codificador = configuracion.codificadorJwt(clave);
    private final SesionService sesiones = mock(SesionService.class);
    private final JwtDecoder decodificador = configuracion.decodificadorJwt(clave, propiedades,
            new ValidadorSesionVigente(sesiones));

    @Test
    @DisplayName("El claim rol se convierte en la autoridad ROLE_HUESPED")
    void convierteRolEnAutoridad() {
        Jwt jwt = decodificador.decode(emitir(c -> { }));

        var autenticacion = configuracion.convertidorJwt().convert(jwt);

        assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_HUESPED");
    }

    @Test
    @DisplayName("Un token válido firmado con HS256 se decodifica")
    void decodificaTokenValido() {
        Jwt jwt = decodificador.decode(emitir(c -> { }));

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getClaimAsString("rol")).isEqualTo("HUESPED");
    }

    @Test
    @DisplayName("Rechaza un token sin exp")
    void rechazaSinExp() {
        String token = emitir(c -> c.claims(m -> m.remove("exp")));

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Rechaza un token sin jti")
    void rechazaSinJti() {
        String token = emitir(c -> c.claims(m -> m.remove("jti")));

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Rechaza un token vencido fuera del desfase de reloj")
    void rechazaVencido() {
        Instant hace = Instant.now().minus(Duration.ofMinutes(5));
        String token = emitir(c -> c.issuedAt(hace.minusSeconds(60)).expiresAt(hace));

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Acepta un token vencido hace 30 s por el desfase de reloj de 60 s")
    void aceptaVencidoDentroDelDesfase() {
        Instant hace = Instant.now().minusSeconds(30);
        String token = emitir(c -> c.issuedAt(hace.minusSeconds(60)).expiresAt(hace));

        assertThat(decodificador.decode(token).getId()).isNotNull();
    }

    @Test
    @DisplayName("Rechaza un token de otro emisor")
    void rechazaOtroEmisor() {
        String token = emitir(c -> c.issuer("otro-emisor"));

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Rechaza un token con la firma alterada")
    void rechazaFirmaAlterada() {
        String[] partes = emitir(c -> { }).split("[.]");
        char primera = partes[2].charAt(0);
        String firma = (primera == 'A' ? 'B' : 'A') + partes[2].substring(1);
        String token = partes[0] + "." + partes[1] + "." + firma;

        assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token válido pero en la denylist es rechazado")
    void rechazaTokenInvalidado() {
        when(sesiones.estaInvalidada(anyString())).thenReturn(true);

        assertThatThrownBy(() -> decodificador.decode(emitir(c -> { }))).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("La denylist no se consulta si un validador base ya falló")
    void noConsultaDenylistSiFallaUnoBase() {
        Instant hace = Instant.now().minus(Duration.ofMinutes(5));
        String vencido = emitir(c -> c.issuedAt(hace.minusSeconds(60)).expiresAt(hace));
        String otroEmisor = emitir(c -> c.issuer("otro-emisor"));
        String sinJti = emitir(c -> c.claims(m -> m.remove("jti")));

        for (String token : new String[] {vencido, otroEmisor, sinJti}) {
            assertThatThrownBy(() -> decodificador.decode(token)).isInstanceOf(JwtException.class);
        }

        // any() y no anyString(): este último no casa con null y no cubriría el caso sin jti.
        verify(sesiones, never()).estaInvalidada(any());
    }

    private String emitir(Consumer<JwtClaimsSet.Builder> ajuste) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject("1")
                .id(UUID.randomUUID().toString())
                .claim("rol", "HUESPED")
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(60)));
        ajuste.accept(claims);
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims.build())).getTokenValue();
    }
}
