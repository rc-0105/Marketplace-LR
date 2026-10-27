package co.edu.unbosque.marketplace.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Firma y verifica los JWT de sesión con una clave simétrica HS256 (D2, D3, D4). */
@Configuration
@EnableConfigurationProperties(PropiedadesJwt.class)
public class ConfiguracionJwt {

    /** Tolerancia entre el reloj de quien emite y el de quien verifica. */
    public static final Duration DESFASE_RELOJ = Duration.ofSeconds(60);

    /** Reloj inyectable: los tests fijan la hora para comprobar iat y exp (D13). */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey claveJwt(PropiedadesJwt propiedades) {
        return new SecretKeySpec(propiedades.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder codificadorJwt(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    public JwtDecoder decodificadorJwt(SecretKey claveJwt, PropiedadesJwt propiedades) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(claveJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // JwtTimestampValidator acepta tokens sin exp, por eso se exige el claim.
        OAuth2TokenValidator<Jwt> validadores = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(DESFASE_RELOJ),
                new JwtIssuerValidator(propiedades.emisor()),
                new JwtClaimValidator<Object>("exp", Objects::nonNull),
                new JwtClaimValidator<Object>("jti", Objects::nonNull));
        decodificador.setJwtValidator(validadores);
        return decodificador;
    }

    /** El claim rol llega como texto ("HUESPED") y se vuelve la autoridad ROLE_HUESPED. */
    @Bean
    public JwtAuthenticationConverter convertidorJwt() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }
}
