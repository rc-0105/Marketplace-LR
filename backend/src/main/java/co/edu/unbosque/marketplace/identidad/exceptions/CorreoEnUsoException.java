package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class CorreoEnUsoException extends NegocioException {

    public CorreoEnUsoException() {
        super(HttpStatus.CONFLICT, "El correo ya está en uso.");
    }
}
