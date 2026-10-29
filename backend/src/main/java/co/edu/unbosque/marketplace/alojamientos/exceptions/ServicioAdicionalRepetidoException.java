package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Dos servicios adicionales de la misma publicación tienen el mismo nombre, sin importar mayúsculas. */
public class ServicioAdicionalRepetidoException extends NegocioException {

    public ServicioAdicionalRepetidoException() {
        super(HttpStatus.BAD_REQUEST, "Hay servicios adicionales con el mismo nombre.");
    }
}
