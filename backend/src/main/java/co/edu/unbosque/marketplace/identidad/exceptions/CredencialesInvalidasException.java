package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Mismo mensaje para correo inexistente, contraseña errónea o cuenta sin acceso (anti-enumeración). */
public class CredencialesInvalidasException extends NegocioException {

    public CredencialesInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos. Revísalos e intenta de nuevo.");
    }
}
