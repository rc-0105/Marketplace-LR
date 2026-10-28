package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Mismo mensaje para código erróneo, vencido, usado o correo inexistente (anti-enumeración). */
public class CodigoRecuperacionInvalidoException extends NegocioException {

    public CodigoRecuperacionInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "El código no es válido o ya venció. Solicita uno nuevo.");
    }
}
