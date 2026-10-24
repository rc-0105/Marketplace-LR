package co.edu.unbosque.marketplace.compartido.infraestructura.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asigna a cada petición un identificador de correlación y lo deja en el MDC, de
 * modo que todos los eventos que genere la petición lo compartan (RNF-24).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    public static final String CLAVE_MDC = "correlacion";

    // Solo se acepta el identificador del cliente si es corto y sin caracteres
    // raros; así nadie puede inyectar saltos de línea o texto arbitrario en el log.
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String correlacion = resolver(peticion.getHeader(CABECERA));
        MDC.put(CLAVE_MDC, correlacion);
        respuesta.setHeader(CABECERA, correlacion);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(CLAVE_MDC);
        }
    }

    private static String resolver(String recibida) {
        return recibida != null && FORMATO_VALIDO.matcher(recibida).matches()
                ? recibida
                : UUID.randomUUID().toString();
    }
}
