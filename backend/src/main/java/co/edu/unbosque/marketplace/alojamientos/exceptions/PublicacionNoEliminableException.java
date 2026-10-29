package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Solo se elimina un borrador; una publicación que ya salió al público se pausa. */
public class PublicacionNoEliminableException extends NegocioException {

    public PublicacionNoEliminableException() {
        super(HttpStatus.CONFLICT, "Solo se puede eliminar una publicación en borrador.");
    }
}
