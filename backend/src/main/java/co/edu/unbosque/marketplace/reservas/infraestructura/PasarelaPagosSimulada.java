package co.edu.unbosque.marketplace.reservas.infraestructura;

import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pasarela sin red para desarrollo y pruebas ({@code pagos.modo=simulado}). Responde como Stripe a sus
 * métodos de prueba: {@code pm_card_chargeDeclined*} se rechaza y cualquier otro se aprueba. Un reintento con
 * la misma clave de idempotencia devuelve la misma transacción, igual que la pasarela real.
 */
public class PasarelaPagosSimulada implements PasarelaPagos {

    private static final Logger LOG = LoggerFactory.getLogger(PasarelaPagosSimulada.class);

    // Solo los cobros aprobados, por clave de idempotencia (la referencia de pago de la reserva).
    private final Map<String, String> transacciones = new ConcurrentHashMap<>();

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        if (solicitud.metodoPago().startsWith("pm_card_chargeDeclined")) {
            return new CobroRechazado(solicitud.metodoPago().endsWith("InsufficientFunds")
                    ? "insufficient_funds" : "card_declined");
        }
        String transaccion = transacciones.computeIfAbsent(solicitud.claveIdempotencia(),
                clave -> "sim_" + UUID.randomUUID());
        LOG.info("Pago simulado aprobado para la reserva {} con la transacción {}", solicitud.reservaId(),
                transaccion);
        return new CobroAprobado(transaccion);
    }

    @Override
    public void reembolsar(String transaccionId) {
        LOG.info("Reembolso simulado de la transacción {}", transaccionId);
    }

    @Override
    public Optional<String> buscarCobroAprobado(String referencia) {
        return Optional.ofNullable(transacciones.get(referencia));
    }
}
