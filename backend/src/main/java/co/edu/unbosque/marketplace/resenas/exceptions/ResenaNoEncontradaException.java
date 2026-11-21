package co.edu.unbosque.marketplace.resenas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La reseña que se quiere moderar no existe (SOF-82). */
public class ResenaNoEncontradaException extends NegocioException {

    public ResenaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa reseña.");
    }
}
