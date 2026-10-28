package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** El administrador apuntó a un usuario que no existe. */
public class UsuarioNoEncontradoException extends NegocioException {

    public UsuarioNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "No encontramos ese usuario.");
    }
}
