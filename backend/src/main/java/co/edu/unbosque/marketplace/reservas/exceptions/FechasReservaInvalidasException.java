package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Las fechas de la reserva no tienen sentido, por ejemplo una llegada que ya pasó (SOF-33). */
public class FechasReservaInvalidasException extends NegocioException {

    public FechasReservaInvalidasException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
