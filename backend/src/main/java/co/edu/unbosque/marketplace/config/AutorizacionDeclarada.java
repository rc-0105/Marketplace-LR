package co.edu.unbosque.marketplace.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Filtro transversal de /api/v1 (ADR-5): evalúa aquí mismo el {@code @PreAuthorize}
 * del endpoint, antes de que Spring lea la ruta o el cuerpo, para que una petición
 * sin permiso reciba 401/403 y nunca un 400 de validación. Lo que no declara
 * roles se niega. Por eso la expresión solo puede hablar de roles; la propiedad
 * del recurso se verifica en el service con {@code Propiedad}.
 */
@Component
public class AutorizacionDeclarada implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision NEGADA = new AuthorizationDecision(false);

    private final ObjectProvider<RequestMappingHandlerMapping> mapeo;
    private final Map<String, WebExpressionAuthorizationManager> expresiones = new ConcurrentHashMap<>();

    public AutorizacionDeclarada(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mapeo) {
        this.mapeo = mapeo;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> autenticacion, RequestAuthorizationContext contexto) {
        PreAuthorize declaracion = declaracion(contexto.getRequest());
        if (declaracion == null) {
            return NEGADA;
        }
        try {
            return expresiones.computeIfAbsent(declaracion.value(), WebExpressionAuthorizationManager::new)
                    .check(autenticacion, contexto);
        } catch (RuntimeException error) {
            // Una expresión que no se puede evaluar sin el método (#parametro) se niega.
            return NEGADA;
        }
    }

    private PreAuthorize declaracion(HttpServletRequest peticion) {
        // getHandler necesita la ruta ya parseada y escribe atributos: se hace sobre una copia aislada.
        HttpServletRequest aislada = new PeticionAislada(peticion);
        ServletRequestPathUtils.parseAndCache(aislada);
        try {
            HandlerExecutionChain cadena = mapeo.getObject().getHandler(aislada);
            return cadena != null && cadena.getHandler() instanceof HandlerMethod metodo ? declaracion(metodo) : null;
        } catch (Exception error) {
            // Método no soportado, tipo de contenido no aceptado...: ante la duda se niega.
            return null;
        }
    }

    /** La anotación del método manda sobre la de la clase, igual que en la seguridad de métodos. */
    private static PreAuthorize declaracion(HandlerMethod metodo) {
        PreAuthorize delMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
        return delMetodo != null
                ? delMetodo
                : AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
    }

    /** Lee los atributos de la petición real, pero guarda los que se escriban en un mapa propio. */
    private static final class PeticionAislada extends HttpServletRequestWrapper {

        private final Map<String, Object> atributos = new HashMap<>();

        PeticionAislada(HttpServletRequest peticion) {
            super(peticion);
        }

        @Override
        public Object getAttribute(String nombre) {
            return atributos.containsKey(nombre) ? atributos.get(nombre) : super.getAttribute(nombre);
        }

        @Override
        public void setAttribute(String nombre, Object valor) {
            atributos.put(nombre, valor);
        }

        @Override
        public void removeAttribute(String nombre) {
            atributos.put(nombre, null);
        }
    }
}
