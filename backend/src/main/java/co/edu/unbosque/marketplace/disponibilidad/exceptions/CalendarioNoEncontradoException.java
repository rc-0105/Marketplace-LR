package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La publicación no existe o no es pública para quien consulta. Es la misma respuesta en los dos casos,
 * para no revelar que una publicación existe (RF-25).
 */
public class CalendarioNoEncontradoException extends NegocioException {

    public CalendarioNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa publicación.");
    }
}
