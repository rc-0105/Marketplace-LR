package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * Otra reserva ya ocupa esas noches de la publicación (SOF-33, ADR-3). Cubre tanto a quien
 * llegó después de que otro huésped confirmó (AC2) como a dos solicitudes simultáneas de las
 * que solo una puede ganar (AC3): la restricción de exclusión de PostgreSQL decide cuál.
 */
public class ReservaSuperpuestaException extends NegocioException {

    public ReservaSuperpuestaException() {
        super(HttpStatus.CONFLICT, "Esas fechas ya no están disponibles: alguien más reservó primero.");
    }
}
