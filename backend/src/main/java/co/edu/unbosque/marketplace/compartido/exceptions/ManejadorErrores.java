package co.edu.unbosque.marketplace.compartido.exceptions;

import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.SolicitudEnProcesoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Único traductor de errores a HTTP. Responde con ProblemDetail (RFC 9457) para
 * que el frontend lea siempre la misma forma.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final String FORMATO_INVALIDO = "El valor no tiene el formato esperado (por ejemplo, 2027-01-15).";

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail negocio(NegocioException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(error.getEstado(), error.getMessage());
        error.getPropiedades().forEach(problema::setProperty);
        return problema;
    }

    /** Más específico que el de {@link NegocioException}: además del problema agrega {@code Retry-After}. */
    @ExceptionHandler(SolicitudEnProcesoException.class)
    public ResponseEntity<ProblemDetail> solicitudEnProceso(SolicitudEnProcesoException error) {
        return ResponseEntity.status(error.getEstado())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(error.getSegundosReintento()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(negocio(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException error) {
        Map<String, String> errores = new LinkedHashMap<>();
        // Un fallo de conversión (una fecha mal escrita) trae un texto técnico en inglés: no se expone.
        error.getBindingResult().getFieldErrors().forEach(campo -> errores.putIfAbsent(campo.getField(),
                campo.isBindingFailure() ? FORMATO_INVALIDO : campo.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud tiene datos inválidos.");
        problema.setProperty("errores", errores);
        return problema;
    }

    // Spring corta la subida antes de llegar al servicio; el límite de application.yml coincide con el del servicio.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail archivoMuyGrande(MaxUploadSizeExceededException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen pesa más de 5 MB.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail archivoAusente(MissingServletRequestPartException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Adjunta la imagen en el campo «archivo».");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException error) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido.");
    }
}
