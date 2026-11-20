package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * El huésped ya tiene una reserva vigente con las mismas fechas pero otros servicios adicionales (SOF-33,
 * ADR-4). El reintento idempotente solo devuelve la reserva si los extras también coinciden; devolverla
 * en silencio le ocultaría al huésped que lo que eligió ahora no es lo que quedó reservado.
 */
public class ReservaConOtrosServiciosException extends NegocioException {

    public ReservaConOtrosServiciosException() {
        super(HttpStatus.CONFLICT,
                "Ya tienes una reserva vigente para esas fechas con otros servicios adicionales.");
    }
}
