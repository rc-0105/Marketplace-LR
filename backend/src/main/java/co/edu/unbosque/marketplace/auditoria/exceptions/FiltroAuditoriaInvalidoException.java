package co.edu.unbosque.marketplace.auditoria.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** El rango de fechas es incoherente o el evento no es una acción crítica conocida (REQ-CA3). */
public class FiltroAuditoriaInvalidoException extends NegocioException {

    public FiltroAuditoriaInvalidoException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
