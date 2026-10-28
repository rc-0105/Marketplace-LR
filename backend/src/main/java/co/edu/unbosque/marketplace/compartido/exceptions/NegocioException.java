package co.edu.unbosque.marketplace.compartido.exceptions;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio de todos los módulos. Cada una declara el
 * estado HTTP con que la traduce {@link ManejadorErrores}.
 */
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus estado;
    // Nunca se serializa: ManejadorErrores la traduce a ProblemDetail en el mismo proceso.
    private final transient Map<String, Object> propiedades;

    protected NegocioException(HttpStatus estado, String mensaje) {
        this(estado, mensaje, Map.of());
    }

    /** Las propiedades extra viajan en el ProblemDetail; {@code Map.copyOf} rechaza los valores nulos. */
    protected NegocioException(HttpStatus estado, String mensaje, Map<String, Object> propiedades) {
        super(mensaje);
        this.estado = estado;
        this.propiedades = Map.copyOf(propiedades);
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public Map<String, Object> getPropiedades() {
        return propiedades;
    }
}
