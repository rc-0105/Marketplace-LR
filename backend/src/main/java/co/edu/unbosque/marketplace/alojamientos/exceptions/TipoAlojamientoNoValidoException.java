package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Se pidió un id de tipo de alojamiento que no está en el catálogo. */
public class TipoAlojamientoNoValidoException extends NegocioException {

    public TipoAlojamientoNoValidoException() {
        super(HttpStatus.BAD_REQUEST, "El tipo de alojamiento no existe en el catálogo.");
    }
}
