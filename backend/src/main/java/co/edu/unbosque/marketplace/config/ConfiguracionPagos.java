package co.edu.unbosque.marketplace.config;

import co.edu.unbosque.marketplace.reservas.infraestructura.PasarelaPagosSimulada;
import co.edu.unbosque.marketplace.reservas.infraestructura.PasarelaPagosStripe;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige la única pasarela de pagos (SOF-34, RF-37) según {@code pagos.modo}: "simulado" (por defecto) o
 * "stripe". Con Stripe la clave debe ser de prueba ({@code sk_test_}): la aplicación no arranca con una clave
 * real, así ninguna configuración puede hacer un cobro de verdad.
 */
@Configuration
public class ConfiguracionPagos {

    private static final String PREFIJO_CLAVE_PRUEBA = "sk_test_";

    @Bean
    public PasarelaPagos pasarelaPagos(
            @Value("${pagos.modo:simulado}") String modo,
            @Value("${pagos.stripe.clave-prueba:}") String clavePrueba,
            @Value("${pagos.tiempo-espera-segundos:10}") long segundosEspera,
            @Value("${pagos.stripe.url-base:}") String urlBase) {
        return switch (modo.trim().toLowerCase(Locale.ROOT)) {
            case "simulado" -> new PasarelaPagosSimulada();
            case "stripe" -> new PasarelaPagosStripe(exigirClavePrueba(clavePrueba),
                    Duration.ofSeconds(segundosEspera), urlBase);
            default -> throw new IllegalStateException(
                    "PAGOS_MODO debe ser 'simulado' o 'stripe', no '%s'.".formatted(modo));
        };
    }

    private static String exigirClavePrueba(String clave) {
        String limpia = clave == null ? "" : clave.trim();
        if (!limpia.startsWith(PREFIJO_CLAVE_PRUEBA) || limpia.length() == PREFIJO_CLAVE_PRUEBA.length()) {
            throw new IllegalStateException(
                    "Con PAGOS_MODO=stripe, STRIPE_CLAVE_PRUEBA debe ser una clave de prueba de Stripe (sk_test_...): "
                            + "no se admiten pagos reales.");
        }
        return limpia;
    }
}
