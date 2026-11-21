package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La reserva no admite una cancelación (SOF-75): ya no está CONFIRMADA (sigue PENDIENTE, ya está CANCELADA
 * o VENCIDA, o perdió la carrera con otra actualización) o su estancia ya terminó. El mensaje distingue cuál
 * de los motivos aplica.
 */
public class ReservaNoCancelableException extends NegocioException {

    public ReservaNoCancelableException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
