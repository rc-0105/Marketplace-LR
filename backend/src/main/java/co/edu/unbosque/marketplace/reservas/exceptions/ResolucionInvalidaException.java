package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * La resolución de una reserva en revisión llegó incompleta (SOF-83): cancelar exige un motivo. Responde 400
 * con el mismo mapa {@code errores} que una validación de campos.
 */
public class ResolucionInvalidaException extends NegocioException {

    public ResolucionInvalidaException(String campo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, "La solicitud tiene datos inválidos.", Map.of("errores", Map.of(campo, mensaje)));
    }
}
