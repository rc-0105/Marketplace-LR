package co.edu.unbosque.marketplace.compartido.idempotencia;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Resultado de una operación idempotente: código y cuerpo JSON ya serializado. Entregar el texto guardado
 * (y no volver a serializar) es lo que hace la réplica idéntica byte a byte.
 */
public record RespuestaIdempotente(int codigoEstado, String cuerpo, boolean replicada) {

    public static final String CABECERA_REPLICA = "Idempotent-Replayed";

    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    /** {@code application/json} en UTF-8; la réplica agrega {@code Idempotent-Replayed: true}. */
    public ResponseEntity<String> comoRespuesta() {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(codigoEstado).contentType(JSON_UTF8);
        if (replicada) {
            respuesta.header(CABECERA_REPLICA, "true");
        }
        return respuesta.body(cuerpo);
    }
}
