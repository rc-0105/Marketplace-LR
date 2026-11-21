package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La reserva no está en revisión manual, o otra resolución la cambió primero (SOF-83): 409. */
public class ReservaNoResolubleException extends NegocioException {

    public ReservaNoResolubleException() {
        super(HttpStatus.CONFLICT, "Solo se puede resolver una reserva en revisión manual.");
    }
}
