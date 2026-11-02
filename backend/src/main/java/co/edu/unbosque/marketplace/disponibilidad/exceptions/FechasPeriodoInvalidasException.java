package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La fecha final no es posterior a la inicial, o la inicial ya pasó (RF-23). */
public class FechasPeriodoInvalidasException extends NegocioException {

    public FechasPeriodoInvalidasException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
