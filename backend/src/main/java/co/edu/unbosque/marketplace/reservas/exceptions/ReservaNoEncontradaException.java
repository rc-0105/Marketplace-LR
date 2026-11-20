package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** No existe una reserva con ese identificador (SOF-34). */
public class ReservaNoEncontradaException extends NegocioException {

    public ReservaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa reserva.");
    }
}
