package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.config.PropiedadesJwt;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Firma el JWT de sesión: sub es el id (nunca el correo) y jti identifica la sesión. */
@Component
public class EmisorTokens {

    private final JwtEncoder codificador;
    private final PropiedadesJwt propiedades;
    private final Clock reloj;

    public EmisorTokens(JwtEncoder codificador, PropiedadesJwt propiedades, Clock reloj) {
        this.codificador = codificador;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public TokenEmitido emitir(Usuario usuario) {
        // El JWT guarda segundos: truncar aquí evita que expiraEn de la respuesta difiera del exp firmado.
        Instant emitidoEn = reloj.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = emitidoEn.plus(Duration.ofMinutes(propiedades.expiracionMinutos()));
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject(String.valueOf(usuario.getId()))
                .id(jti)
                .claim("rol", usuario.getRol().name())
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .build();
        // Sin el header explícito Nimbus intenta RS256 y no encuentra la clave (D3).
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String valor = codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(valor, jti, expiraEn);
    }
}
