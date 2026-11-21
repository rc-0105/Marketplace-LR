package co.edu.unbosque.marketplace.compartido.idempotencia.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Una clave vigente ya se usó con otra solicitud distinta: error del cliente, no se ejecuta nada (REQ-IS6). */
public class ClaveIdempotenciaReutilizadaException extends NegocioException {

    public ClaveIdempotenciaReutilizadaException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Esa clave de idempotencia ya se usó con una solicitud distinta. Genera una clave nueva.",
                Map.of("codigo", "IDEMPOTENCIA_CLAVE_REUTILIZADA"));
    }
}
