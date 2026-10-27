package co.edu.unbosque.marketplace.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de los JWT. El secreto llega solo por entorno (RNF-17) y HS256 exige
 * al menos 32 bytes; si no se cumple la aplicación no arranca (REQ-A11).
 */
@Validated
@ConfigurationProperties("jwt")
public record PropiedadesJwt(
        String secreto,
        @DefaultValue("60") int expiracionMinutos,
        @DefaultValue("marketplace-app") String emisor) {

    private static final int BYTES_MINIMOS = 32;

    public PropiedadesJwt {
        // El mensaje nunca incluye el valor del secreto.
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS) {
            throw new IllegalArgumentException(
                    "JWT_SECRET es obligatorio y debe tener al menos " + BYTES_MINIMOS + " bytes.");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalArgumentException("jwt.expiracionMinutos debe ser mayor que 0.");
        }
    }

    @Override
    public String toString() {
        return "PropiedadesJwt[expiracionMinutos=" + expiracionMinutos + ", emisor=" + emisor + "]";
    }
}
