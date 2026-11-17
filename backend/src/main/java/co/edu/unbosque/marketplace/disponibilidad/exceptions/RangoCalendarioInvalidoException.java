package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La ventana del calendario termina antes de empezar o es más larga que el máximo permitido (RF-25). */
public class RangoCalendarioInvalidoException extends NegocioException {

    public RangoCalendarioInvalidoException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
