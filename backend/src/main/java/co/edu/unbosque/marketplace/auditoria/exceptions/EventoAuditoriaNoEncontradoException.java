package co.edu.unbosque.marketplace.auditoria.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

public class EventoAuditoriaNoEncontradoException extends NegocioException {

    public EventoAuditoriaNoEncontradoException() {
        super(HttpStatus.NOT_FOUND, "No encontramos ese evento de auditoría.");
    }
}
