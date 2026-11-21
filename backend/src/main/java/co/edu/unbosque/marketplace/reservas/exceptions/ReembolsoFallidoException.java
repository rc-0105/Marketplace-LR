package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La pasarela no completó la devolución al cancelar una reserva en revisión manual (SOF-83): 502. La reserva
 * sigue en revisión y nada cambió, así que el administrador puede intentar de nuevo.
 */
public class ReembolsoFallidoException extends NegocioException {

    public ReembolsoFallidoException() {
        super(HttpStatus.BAD_GATEWAY, "No se pudo procesar la devolución en la pasarela de pagos. Intenta de nuevo.");
    }
}
