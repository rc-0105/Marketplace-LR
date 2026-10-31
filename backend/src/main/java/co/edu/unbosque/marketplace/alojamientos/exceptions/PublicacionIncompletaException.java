package co.edu.unbosque.marketplace.alojamientos.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Un borrador incompleto no se activa; la respuesta lista exactamente qué le falta (RF-14). */
public class PublicacionIncompletaException extends NegocioException {

    public PublicacionIncompletaException(List<String> camposFaltantes) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Faltan campos para activar la publicación.",
                Map.of("camposFaltantes", List.copyOf(camposFaltantes)));
    }
}
