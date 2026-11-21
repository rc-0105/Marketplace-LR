package co.edu.unbosque.marketplace.notificaciones.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Una notificación que no existe o es de otro usuario: el mismo 404, para no revelar que existe. */
public class NotificacionNoEncontradaException extends NegocioException {

    public NotificacionNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "No encontramos esa notificación.");
    }
}
