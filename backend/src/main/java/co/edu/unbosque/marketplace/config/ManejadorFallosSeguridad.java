package co.edu.unbosque.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Traduce los fallos que nacen en el filtro de seguridad (antes de llegar a un
 * controlador) a la misma forma ProblemDetail que ManejadorErrores (REQ-A9).
 * El mensaje es genérico: nunca se explica por qué se rechazó el token.
 */
@Component
public class ManejadorFallosSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler,
        AuthenticationFailureHandler {

    /** Tope al recorrer las causas: una cadena cíclica no debe colgar la petición. */
    private static final int PROFUNDIDAD_MAXIMA_CAUSAS = 10;

    private final ObjectMapper mapper;

    public ManejadorFallosSeguridad(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta,
            AuthenticationException error) throws IOException {
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(peticion, respuesta, HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para continuar.");
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta,
            AccessDeniedException error) throws IOException {
        escribir(peticion, respuesta, HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción.");
    }

    /**
     * Fallos del filtro Bearer: si la denylist no se pudo consultar es un 503
     * (el token no es inválido), y cualquier otro fallo sigue siendo un 401.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest peticion, HttpServletResponse respuesta,
            AuthenticationException error) throws IOException {
        Throwable causa = error;
        for (int nivel = 0; causa != null && nivel < PROFUNDIDAD_MAXIMA_CAUSAS; nivel++) {
            if (causa instanceof SesionNoVerificableException) {
                escribir(peticion, respuesta, HttpStatus.SERVICE_UNAVAILABLE,
                        "El servicio no está disponible en este momento. Intenta de nuevo en unos minutos.");
                return;
            }
            causa = causa.getCause();
        }
        commence(peticion, respuesta, error);
    }

    private void escribir(HttpServletRequest peticion, HttpServletResponse respuesta,
            HttpStatus estado, String detalle) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setInstance(URI.create(peticion.getRequestURI()));
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        mapper.writeValue(respuesta.getWriter(), problema);
    }
}
