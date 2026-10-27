package co.edu.unbosque.marketplace.compartido.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio de todos los módulos. Cada una declara el
 * estado HTTP con que la traduce {@link ManejadorErrores}.
 */
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus estado;

    protected NegocioException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
