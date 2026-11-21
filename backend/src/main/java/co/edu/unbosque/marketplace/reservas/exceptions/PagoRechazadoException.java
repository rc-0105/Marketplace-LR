package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * La pasarela rechazó el cobro (SOF-34, AC2). Para entonces la reserva ya quedó CANCELADA y sus noches
 * libres; el motivo de la pasarela (p. ej. {@code card_declined}) viaja en el ProblemDetail.
 */
public class PagoRechazadoException extends NegocioException {

    public PagoRechazadoException(String motivo) {
        super(HttpStatus.PAYMENT_REQUIRED,
                "La pasarela rechazó el pago: la reserva no se confirmó y las fechas quedaron libres.",
                Map.of("motivo", motivo == null ? "desconocido" : motivo));
    }
}
