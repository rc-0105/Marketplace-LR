package co.edu.unbosque.marketplace.reservas.services;

import java.math.BigDecimal;

/**
 * Puerto hacia la pasarela de pagos (SOF-34, RF-37). El adaptador lo elige {@code pagos.modo}: "simulado"
 * (por defecto, sin red) o "stripe" (Stripe en modo prueba). Ninguno hace pagos reales.
 */
public interface PasarelaPagos {

    /**
     * Cobra el monto con el método de pago de prueba. Devuelve el resultado si la pasarela respondió, haya
     * aprobado o rechazado; si no respondió a tiempo o falló, lanza {@link FallaPasarelaException}. La clave
     * de idempotencia hace que un reintento del mismo cobro no se cobre dos veces.
     */
    ResultadoCobro cobrar(SolicitudCobro solicitud);

    /** Devuelve por completo un cobro aprobado que no pudo confirmar ninguna reserva. */
    void reembolsar(String transaccionId);

    record SolicitudCobro(Long reservaId, BigDecimal monto, String metodoPago, String claveIdempotencia) {
    }

    sealed interface ResultadoCobro {
    }

    record CobroAprobado(String transaccionId) implements ResultadoCobro {
    }

    record CobroRechazado(String motivo) implements ResultadoCobro {
    }
}
