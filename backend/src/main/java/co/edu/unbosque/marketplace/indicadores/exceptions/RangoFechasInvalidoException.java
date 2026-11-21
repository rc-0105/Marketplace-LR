package co.edu.unbosque.marketplace.indicadores.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La fecha desde es posterior a la fecha hasta (RF-42). */
public class RangoFechasInvalidoException extends NegocioException {

    public RangoFechasInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "La fecha desde no puede ser posterior a la fecha hasta.");
    }
}
