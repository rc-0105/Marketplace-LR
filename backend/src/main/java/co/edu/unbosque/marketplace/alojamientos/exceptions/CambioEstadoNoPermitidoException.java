package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** El estado actual de la publicación no admite el cambio pedido, por ejemplo pausar un borrador. */
public class CambioEstadoNoPermitidoException extends NegocioException {

    public CambioEstadoNoPermitidoException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
