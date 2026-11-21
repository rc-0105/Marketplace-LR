package co.edu.unbosque.marketplace.indicadores.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/** El parámetro {@code formato} no es pdf ni xlsx (RF-43). */
public class FormatoExportacionDesconocidoException extends NegocioException {

    public FormatoExportacionDesconocidoException() {
        super(HttpStatus.BAD_REQUEST, "Formato de exportación desconocido. Use pdf o xlsx.");
    }
}
