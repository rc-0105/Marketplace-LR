package co.edu.unbosque.marketplace.compartido.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Único traductor de errores a HTTP. Responde con ProblemDetail (RFC 9457) para
 * que el frontend lea siempre la misma forma.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail negocio(NegocioException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(error.getEstado(), error.getMessage());
        error.getPropiedades().forEach(problema::setProperty);
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException error) {
        Map<String, String> errores = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors()
                .forEach(campo -> errores.putIfAbsent(campo.getField(), campo.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud tiene datos inválidos.");
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException error) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido.");
    }
}
