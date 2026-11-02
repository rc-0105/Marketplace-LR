package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class PeriodoSuperpuestoException extends NegocioException {

    public PeriodoSuperpuestoException() {
        super(HttpStatus.CONFLICT, "El periodo se cruza con otro periodo disponible de la publicación.");
    }
}
