package co.edu.unbosque.marketplace.resenas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La reserva ya tiene su reseña: solo se reseña una vez (SOF-80, AC3). */
public class ResenaYaExisteException extends NegocioException {

    public ResenaYaExisteException() {
        super(HttpStatus.CONFLICT, "Ya existe una reseña para esta reserva.");
    }
}
