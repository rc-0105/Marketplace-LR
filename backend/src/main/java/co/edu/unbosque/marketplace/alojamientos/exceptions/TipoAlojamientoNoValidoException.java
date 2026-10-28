package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class TipoAlojamientoNoValidoException extends NegocioException {

    public TipoAlojamientoNoValidoException() {
        super(HttpStatus.BAD_REQUEST, "El tipo debe ser CASA, APARTAMENTO o HABITACION.");
    }
}
