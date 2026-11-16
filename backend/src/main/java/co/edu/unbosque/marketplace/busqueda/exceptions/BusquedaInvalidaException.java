package co.edu.unbosque.marketplace.busqueda.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La ciudad falta o la salida no es posterior a la llegada: la búsqueda no se ejecuta (RF-28). */
public class BusquedaInvalidaException extends NegocioException {

    public BusquedaInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
