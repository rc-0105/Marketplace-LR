package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** La cuenta está restringida por un administrador; el motivo se le muestra a su dueño. */
public class CuentaRestringidaException extends NegocioException {

    public CuentaRestringidaException(String motivo) {
        super(HttpStatus.FORBIDDEN, "Tu cuenta está restringida.",
                Map.of("codigo", "CUENTA_RESTRINGIDA", "motivo", motivo));
    }
}
