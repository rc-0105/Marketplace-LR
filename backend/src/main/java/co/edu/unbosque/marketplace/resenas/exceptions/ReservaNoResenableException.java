package co.edu.unbosque.marketplace.resenas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La reserva todavía no admite una reseña (SOF-80, AC2): su estancia no ha terminado o la reserva no llegó a
 * COMPLETADA. El mensaje indica cuál es el caso.
 */
public class ReservaNoResenableException extends NegocioException {

    public ReservaNoResenableException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
