package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Se pidió reactivar una cuenta que ya está activa. */
public class CuentaYaActivaException extends NegocioException {

    public CuentaYaActivaException() {
        super(HttpStatus.CONFLICT, "Tu cuenta ya está activa.", Map.of("codigo", "CUENTA_ACTIVA"));
    }
}
