package co.edu.unbosque.marketplace.compartido.exceptions;

import org.springframework.http.HttpStatus;

/**
 * El usuario tiene el rol correcto pero el recurso no es suyo (RF-04). Usa el
 * mismo mensaje que el 403 del filtro de seguridad para no revelar más.
 */
public class AccesoDenegadoException extends NegocioException {

    public AccesoDenegadoException() {
        super(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción.");
    }
}
