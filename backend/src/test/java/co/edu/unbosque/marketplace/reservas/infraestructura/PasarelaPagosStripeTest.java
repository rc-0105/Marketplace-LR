package co.edu.unbosque.marketplace.reservas.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.reservas.services.FallaPasarelaException;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroAprobado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroRechazado;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.SolicitudCobro;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Adaptador de Stripe contra un servidor HTTP local que imita la API (SOF-34): ninguna prueba sale a la red ni
 * necesita una clave real.
 */
class PasarelaPagosStripeTest {

    private static final SolicitudCobro SOLICITUD = new SolicitudCobro(42L, new BigDecimal("485000.00"),
            "pm_card_visa", "reserva-42-pm_card_visa");

    private HttpServer servidor;
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> idempotencia = new AtomicReference<>();
    private volatile int estado;
    private volatile String respuesta;
    private volatile long demoraMs;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Un PaymentIntent succeeded se aprueba con su id, cobrando el total en centavos de COP")
    void unCobroExitosoSeApruebaConLaTransaccion() {
        responderCon(200, "{\"id\":\"pi_123\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}");

        assertThat(pasarela(Duration.ofSeconds(5)).cobrar(SOLICITUD)).isEqualTo(new CobroAprobado("pi_123"));

        assertThat(ruta.get()).isEqualTo("/v1/payment_intents");
        assertThat(cuerpo.get()).contains("amount=48500000", "currency=cop", "payment_method=pm_card_visa",
                "confirm=true", "metadata[reservaId]=42");
        assertThat(idempotencia.get()).isEqualTo("reserva-42-pm_card_visa");
    }

    @Test
    @DisplayName("Un rechazo de la tarjeta (402 card_error) se devuelve como cobro rechazado con su motivo")
    void unaTarjetaRechazadaSeDevuelveComoRechazo() {
        responderCon(402, "{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\","
                + "\"decline_code\":\"generic_decline\",\"message\":\"Your card was declined.\"}}");

        assertThat(pasarela(Duration.ofSeconds(5)).cobrar(SOLICITUD)).isEqualTo(new CobroRechazado("generic_decline"));
    }

    @Test
    @DisplayName("Un PaymentIntent que no quedó succeeded no se cuenta como cobrado")
    void unIntentoQueNoTerminoNoSeAprueba() {
        responderCon(200, "{\"id\":\"pi_9\",\"object\":\"payment_intent\",\"status\":\"requires_action\"}");

        assertThat(pasarela(Duration.ofSeconds(5)).cobrar(SOLICITUD)).isEqualTo(new CobroRechazado("requires_action"));
    }

    @Test
    @DisplayName("Si Stripe no responde dentro del tiempo de espera, falla como sin respuesta")
    void sinRespuestaATiempoFallaComoSinRespuesta() {
        demoraMs = 3000;
        responderCon(200, "{\"id\":\"pi_tarde\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}");

        assertThatThrownBy(() -> pasarela(Duration.ofSeconds(1)).cobrar(SOLICITUD))
                .isInstanceOfSatisfying(FallaPasarelaException.class,
                        falla -> assertThat(falla.isSinRespuesta()).isTrue());
    }

    @Test
    @DisplayName("Un error de Stripe que no es de la tarjeta (clave inválida) falla, pero no como sin respuesta")
    void unErrorDeStripeFallaSinSerTiempoAgotado() {
        responderCon(401, "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Invalid API Key provided\"}}");

        assertThatThrownBy(() -> pasarela(Duration.ofSeconds(5)).cobrar(SOLICITUD))
                .isInstanceOfSatisfying(FallaPasarelaException.class,
                        falla -> assertThat(falla.isSinRespuesta()).isFalse());
    }

    @Test
    @DisplayName("El reembolso devuelve el PaymentIntent con su propia clave de idempotencia")
    void elReembolsoApuntaAlPaymentIntent() {
        responderCon(200, "{\"id\":\"re_1\",\"object\":\"refund\",\"status\":\"succeeded\"}");

        pasarela(Duration.ofSeconds(5)).reembolsar("pi_123");

        assertThat(ruta.get()).isEqualTo("/v1/refunds");
        assertThat(cuerpo.get()).contains("payment_intent=pi_123");
        assertThat(idempotencia.get()).isEqualTo("reembolso-pi_123");
    }

    private PasarelaPagosStripe pasarela(Duration espera) {
        return new PasarelaPagosStripe("sk_test_falsa", espera, "http://localhost:" + servidor.getAddress().getPort());
    }

    private void responderCon(int estado, String respuesta) {
        this.estado = estado;
        this.respuesta = respuesta;
    }

    private void responder(HttpExchange intercambio) throws IOException {
        ruta.set(intercambio.getRequestURI().getPath());
        idempotencia.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
        cuerpo.set(URLDecoder.decode(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));
        if (demoraMs > 0) {
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        try {
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
        } catch (IOException clienteSeFue) {
            // El cliente ya cortó por tiempo de espera.
        } finally {
            intercambio.close();
        }
    }
}
