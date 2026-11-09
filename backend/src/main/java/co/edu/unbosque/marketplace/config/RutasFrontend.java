package co.edu.unbosque.marketplace.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * En producción la misma aplicación sirve el build de React (SOF-97): así frontend y API
 * comparten origen y no hace falta CORS. Las rutas del SPA, como
 * {@code /anfitrion/publicaciones}, no existen como archivo y devuelven {@code index.html}
 * para que React Router las resuelva en el navegador.
 */
@Controller
public class RutasFrontend {

    // Lo que no es del frontend: la API, el actuator, el contrato OpenAPI y los
    // archivos que genera Vite, que sí existen y los sirve el manejador de estáticos.
    private static final String NO_ES_PAGINA = "(?!api$|actuator$|assets$|v3$|swagger-ui$|error$)";

    /** Las páginas del frontend son públicas; cada pantalla protegida la cuida la API que llama. */
    static final RequestMatcher PAGINAS = peticion -> HttpMethod.GET.matches(peticion.getMethod())
            && !peticion.getRequestURI().startsWith("/api/")
            && !peticion.getRequestURI().startsWith("/actuator");

    @GetMapping({"/", "/{ruta:" + NO_ES_PAGINA + "[^.]*}", "/{ruta:" + NO_ES_PAGINA + "[^.]*}/**"})
    public String pagina() {
        return "forward:/index.html";
    }
}
