package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import co.edu.unbosque.marketplace.config.ConfiguracionJwt;
import co.edu.unbosque.marketplace.config.PropiedadesJwt;
import co.edu.unbosque.marketplace.config.ValidadorSesionVigente;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class EmisorTokensTest {

    private static final String EMISOR = "marketplace-app";

    private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final ConfiguracionJwt configuracion = new ConfiguracionJwt();
    private final PropiedadesJwt propiedades = new PropiedadesJwt("S".repeat(32), 60, EMISOR);
    private final SecretKey clave = configuracion.claveJwt(propiedades);
    private final JwtDecoder decodificador = configuracion.decodificadorJwt(clave, propiedades,
            new ValidadorSesionVigente(mock(SesionService.class)));
    private final EmisorTokens emisor = new EmisorTokens(
            configuracion.codificadorJwt(clave), propiedades, Clock.fixed(ahora, ZoneOffset.UTC));
    private final Usuario usuario = usuario();

    @Test
    @DisplayName("El token lleva sub con el id, jti UUID, rol, iat, exp a 60 minutos e iss, y no el correo")
    void emiteLosClaimsDeSesion() {
        TokenEmitido token = emisor.emitir(usuario);

        Jwt jwt = decodificador.decode(token.valor());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(UUID.fromString(jwt.getId())).isNotNull();
        assertThat(jwt.getClaimAsString("rol")).isEqualTo("HUESPED");
        assertThat(jwt.getIssuedAt()).isEqualTo(ahora);
        assertThat(jwt.getExpiresAt()).isEqualTo(ahora.plusSeconds(3600));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(EMISOR);
        assertThat(jwt.getClaims().values()).doesNotContain("ana@correo.com");
        assertThat(token.jti()).isEqualTo(jwt.getId());
        assertThat(token.expiraEn()).isEqualTo(ahora.plusSeconds(3600));
    }

    @Test
    @DisplayName("Dos emisiones tienen jti distinto")
    void cadaTokenTieneJtiPropio() {
        assertThat(emisor.emitir(usuario).jti()).isNotEqualTo(emisor.emitir(usuario).jti());
    }

    @Test
    @DisplayName("El rol del token emitido se convierte en la autoridad ROLE_HUESPED")
    void elRolSeConvierteEnAutoridad() {
        Jwt jwt = decodificador.decode(emisor.emitir(usuario).valor());

        assertThat(configuracion.convertidorJwt().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_HUESPED");
    }

    private static Usuario usuario() {
        Usuario nuevo = Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash", Rol.HUESPED);
        ReflectionTestUtils.setField(nuevo, "id", 7L);
        return nuevo;
    }
}
