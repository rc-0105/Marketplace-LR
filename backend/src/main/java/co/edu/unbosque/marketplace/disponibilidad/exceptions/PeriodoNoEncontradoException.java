package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class PeriodoNoEncontradoException extends NegocioException {

    public PeriodoNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "No encontramos ese periodo.");
    }
}
