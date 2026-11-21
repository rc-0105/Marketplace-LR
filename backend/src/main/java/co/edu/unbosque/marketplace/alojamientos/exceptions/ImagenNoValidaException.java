package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** El archivo subido no se puede aceptar como imagen de una publicación. */
public class ImagenNoValidaException extends NegocioException {

    public ImagenNoValidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
