package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * La contraseña de confirmación no coincide. Es 403 y no 401 a propósito: un 401 haría que el cliente
 * cierre la sesión aunque el token sea válido, y aquí solo se rechaza la acción.
 */
public class ConfirmacionInvalidaException extends NegocioException {

    private static final String CODIGO = "CONFIRMACION_INVALIDA";
    private static final String DETALLE = "La contraseña no es correcta.";

    public ConfirmacionInvalidaException() {
        super(HttpStatus.FORBIDDEN, DETALLE, Map.of("codigo", CODIGO));
    }
}
