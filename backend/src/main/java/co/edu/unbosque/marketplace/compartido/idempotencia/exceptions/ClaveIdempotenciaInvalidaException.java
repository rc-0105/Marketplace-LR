package co.edu.unbosque.marketplace.compartido.idempotencia.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** La cabecera {@code Idempotency-Key} falta, está vacía o no es un UUID (SOF-77, REQ-IR1). */
public class ClaveIdempotenciaInvalidaException extends NegocioException {

    public ClaveIdempotenciaInvalidaException() {
        super(HttpStatus.BAD_REQUEST, "Falta la cabecera Idempotency-Key o no es un UUID válido.",
                Map.of("codigo", "IDEMPOTENCIA_CLAVE_INVALIDA"));
    }
}
