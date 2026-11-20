package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.reservas.infraestructura.PasarelaPagosSimulada;
import co.edu.unbosque.marketplace.reservas.infraestructura.PasarelaPagosStripe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Elección de la pasarela y garantía de que nunca se usa una clave real (SOF-34). */
class ConfiguracionPagosTest {

    private final ConfiguracionPagos configuracion = new ConfiguracionPagos();

    @Test
    @DisplayName("El modo simulado no necesita clave")
    void elModoSimuladoNoNecesitaClave() {
        assertThat(configuracion.pasarelaPagos(" Simulado ", "", 10, "")).isInstanceOf(PasarelaPagosSimulada.class);
    }

    @Test
    @DisplayName("El modo stripe arranca con una clave de prueba sk_test_")
    void elModoStripeAceptaUnaClaveDePrueba() {
        assertThat(configuracion.pasarelaPagos("stripe", "sk_test_abc123", 10, ""))
                .isInstanceOf(PasarelaPagosStripe.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "sk_test_", "sk_live_abc123", "rk_live_abc123", "pk_test_abc123"})
    @DisplayName("El modo stripe no arranca sin una clave de prueba")
    void elModoStripeRechazaClavesQueNoSonDePrueba(String clave) {
        assertThatThrownBy(() -> configuracion.pasarelaPagos("stripe", clave, 10, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_CLAVE_PRUEBA debe ser una clave de prueba");
    }

    @Test
    @DisplayName("Un modo desconocido detiene el arranque")
    void unModoDesconocidoDetieneElArranque() {
        assertThatThrownBy(() -> configuracion.pasarelaPagos("paypal", "", 10, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PAGOS_MODO debe ser 'simulado' o 'stripe', no 'paypal'.");
    }
}
