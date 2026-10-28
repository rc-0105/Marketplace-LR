package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** La cuenta está desactivada; su dueño puede reactivarla con sus credenciales. */
public class CuentaDesactivadaException extends NegocioException {

    public CuentaDesactivadaException() {
        super(HttpStatus.FORBIDDEN, "Tu cuenta está desactivada.", Map.of("codigo", "CUENTA_DESACTIVADA"));
    }
}
