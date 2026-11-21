package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** No hay una imagen guardada con ese identificador. */
public class ImagenNoEncontradaException extends NegocioException {

    public ImagenNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa imagen.");
    }
}
