package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** La estancia consultada termina el mismo día que empieza o antes (RF-26). */
public class RangoConsultaInvalidoException extends NegocioException {

    public RangoConsultaInvalidoException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
