package co.edu.unbosque.marketplace.reservas.infraestructura;

import co.edu.unbosque.marketplace.reservas.services.FallaPasarelaException;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentSearchParams;
import com.stripe.param.RefundCreateParams;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

/**
 * Stripe en modo prueba ({@code pagos.modo=stripe}). Crea y confirma en una sola llamada un PaymentIntent con
 * el método de prueba elegido; un rechazo de la tarjeta llega como {@link CardException}. Sin reintentos
 * automáticos: el tiempo de espera es el que manda y la clave de idempotencia cubre el reintento del huésped.
 */
public class PasarelaPagosStripe implements PasarelaPagos {

    // El peso colombiano no tiene centavos en la práctica, pero Stripe lo trata con dos decimales.
    private static final String MONEDA = "cop";

    private final StripeClient cliente;

    public PasarelaPagosStripe(String clavePrueba, Duration tiempoEspera, String urlBase) {
        int milisegundos = Math.toIntExact(tiempoEspera.toMillis());
        StripeClient.StripeClientBuilder constructor = StripeClient.builder().setApiKey(clavePrueba)
                .setConnectTimeout(milisegundos).setReadTimeout(milisegundos).setMaxNetworkRetries(0);
        if (urlBase != null && !urlBase.isBlank()) {
            constructor.setApiBase(urlBase);
        }
        this.cliente = constructor.build();
    }

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        long centavos = solicitud.monto().setScale(0, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        PaymentIntentCreateParams parametros = PaymentIntentCreateParams.builder()
                .setAmount(centavos)
                .setCurrency(MONEDA)
                .setPaymentMethod(solicitud.metodoPago())
                .setConfirm(true)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .setDescription("Reserva " + solicitud.reservaId())
                .putMetadata("reservaId", String.valueOf(solicitud.reservaId()))
                .putMetadata("referencia", solicitud.claveIdempotencia())
                .build();
        try {
            PaymentIntent intento = cliente.v1().paymentIntents().create(parametros,
                    RequestOptions.builder().setIdempotencyKey(solicitud.claveIdempotencia()).build());
            // Con redirecciones prohibidas, lo que no sea "succeeded" (p. ej. requires_action) no se cobró.
            return "succeeded".equals(intento.getStatus())
                    ? new CobroAprobado(intento.getId())
                    : new CobroRechazado(intento.getStatus());
        } catch (CardException rechazo) {
            return new CobroRechazado(rechazo.getDeclineCode() != null ? rechazo.getDeclineCode() : rechazo.getCode());
        } catch (StripeException error) {
            throw traducir("cobrar la reserva " + solicitud.reservaId(), error);
        }
    }

    @Override
    public void reembolsar(String transaccionId) {
        try {
            cliente.v1().refunds().create(RefundCreateParams.builder().setPaymentIntent(transaccionId).build(),
                    RequestOptions.builder().setIdempotencyKey("reembolso-" + transaccionId).build());
        } catch (InvalidRequestException error) {
            // Un reembolso anterior ya devolvió el cobro (la clave de idempotencia dura solo 24 horas).
            if (!"charge_already_refunded".equals(error.getCode())) {
                throw traducir("reembolsar la transacción " + transaccionId, error);
            }
        } catch (StripeException error) {
            throw traducir("reembolsar la transacción " + transaccionId, error);
        }
    }

    // La búsqueda de Stripe puede tardar alrededor de un minuto en reflejar un cobro nuevo: la conciliación le da
    // un margen de gracia antes de cerrar un pago como no cobrado.
    @Override
    public Optional<String> buscarCobroAprobado(String referencia) {
        // Por la referencia y no por el id: un id de reserva se repite entre bases de datos que comparten la clave.
        PaymentIntentSearchParams parametros = PaymentIntentSearchParams.builder()
                .setQuery("metadata['referencia']:'" + referencia + "' AND status:'succeeded'")
                .setLimit(1L)
                .build();
        try {
            return cliente.v1().paymentIntents().search(parametros).getData().stream()
                    .map(PaymentIntent::getId)
                    .findFirst();
        } catch (StripeException error) {
            throw traducir("buscar el cobro " + referencia, error);
        }
    }

    // ApiConnectionException es que no hubo respuesta (tiempo agotado o red); el resto es un error de Stripe.
    private static FallaPasarelaException traducir(String operacion, StripeException error) {
        boolean sinRespuesta = error instanceof ApiConnectionException;
        return new FallaPasarelaException(sinRespuesta, "Stripe no pudo " + operacion
                + (sinRespuesta ? ": no respondió a tiempo." : ": " + error.getMessage()), error);
    }
}
