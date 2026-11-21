package co.edu.unbosque.marketplace.resenas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La reseña ya estaba oculta; no se oculta dos veces (SOF-82). */
public class ResenaYaOcultaException extends NegocioException {

    public ResenaYaOcultaException() {
        super(HttpStatus.CONFLICT, "Esta reseña ya está oculta.");
    }
}
