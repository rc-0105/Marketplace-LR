package co.edu.unbosque.marketplace.cotizacion.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** Las fechas no forman una estancia o un servicio adicional no es de la publicación: no se cotiza (RF-32, RF-33). */
public class CotizacionInvalidaException extends NegocioException {

    public CotizacionInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, mensaje);
    }
}
