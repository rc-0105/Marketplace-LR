package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** La cuenta no está en el estado que la operación pide; el código es siempre el mismo y el detalle dice el caso. */
public class OperacionCuentaNoPermitidaException extends NegocioException {

    public OperacionCuentaNoPermitidaException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje, Map.of("codigo", "OPERACION_CUENTA_NO_PERMITIDA"));
    }
}
