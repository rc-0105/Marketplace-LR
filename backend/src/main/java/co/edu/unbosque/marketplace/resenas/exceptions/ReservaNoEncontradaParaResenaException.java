package co.edu.unbosque.marketplace.resenas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La reserva que se quiere reseñar no existe (SOF-80). */
public class ReservaNoEncontradaParaResenaException extends NegocioException {

    public ReservaNoEncontradaParaResenaException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa reserva.");
    }
}
