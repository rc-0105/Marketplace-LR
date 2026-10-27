package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class RolNoPermitidoException extends NegocioException {

    public RolNoPermitidoException() {
        super(HttpStatus.BAD_REQUEST, "El rol debe ser HUESPED o ANFITRION.");
    }
}
