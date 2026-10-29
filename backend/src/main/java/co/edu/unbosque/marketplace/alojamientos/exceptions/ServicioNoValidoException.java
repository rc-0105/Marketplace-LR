package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Se pidió un id de servicio que no está en el catálogo. */
public class ServicioNoValidoException extends NegocioException {

    public ServicioNoValidoException() {
        super(HttpStatus.BAD_REQUEST, "Uno de los servicios no existe en el catálogo.");
    }
}
