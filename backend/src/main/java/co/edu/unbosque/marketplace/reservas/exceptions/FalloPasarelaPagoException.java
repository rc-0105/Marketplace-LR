package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La pasarela no dio un resultado (SOF-34, AC3): 504 si no respondió dentro del tiempo de espera, 502 si
 * falló. La reserva sigue PENDIENTE con el pago en ERROR, así que el huésped puede reintentar.
 */
public class FalloPasarelaPagoException extends NegocioException {

    public FalloPasarelaPagoException(boolean sinRespuesta) {
        super(sinRespuesta ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                (sinRespuesta
                        ? "La pasarela de pagos no respondió a tiempo."
                        : "La pasarela de pagos no está disponible en este momento.")
                        + " Tu reserva sigue apartada hasta que venza el apartado: intenta pagar de nuevo.");
    }
}
