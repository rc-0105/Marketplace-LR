package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La reserva no admite un pago (SOF-34): ya está pagada, o fue cancelada o su apartado venció. Se responde
 * sin llamar a la pasarela.
 */
public class ReservaNoPagableException extends NegocioException {

    public ReservaNoPagableException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
