package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Se pidió un id de política de cancelación que no está en el catálogo. */
public class PoliticaCancelacionNoValidaException extends NegocioException {

    public PoliticaCancelacionNoValidaException() {
        super(HttpStatus.BAD_REQUEST, "La política de cancelación no existe en el catálogo.");
    }
}
