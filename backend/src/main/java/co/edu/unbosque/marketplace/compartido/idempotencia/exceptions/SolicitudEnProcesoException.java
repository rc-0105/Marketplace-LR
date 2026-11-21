package co.edu.unbosque.marketplace.compartido.idempotencia.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * La misma solicitud sigue ejecutándose (REQ-IS7). Es un 409 distinto del {@code RESERVADO} de las fechas
 * ocupadas: el código lo diferencia y {@code Retry-After} indica cuándo reintentar.
 */
public class SolicitudEnProcesoException extends NegocioException {

    private final int segundosReintento;

    public SolicitudEnProcesoException(int segundosReintento) {
        super(HttpStatus.CONFLICT, "Tu solicitud anterior sigue en curso. Espera un momento y reintenta.",
                Map.of("codigo", "IDEMPOTENCIA_EN_PROCESO"));
        this.segundosReintento = segundosReintento;
    }

    public int getSegundosReintento() {
        return segundosReintento;
    }
}
