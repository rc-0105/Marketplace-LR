package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class PublicacionNoEncontradaException extends NegocioException {

    public PublicacionNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa publicación.");
    }
}
