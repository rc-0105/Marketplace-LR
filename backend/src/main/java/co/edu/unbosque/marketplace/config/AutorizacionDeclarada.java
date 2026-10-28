package co.edu.unbosque.marketplace.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Filtro transversal de /api/v1 (ADR-5): deja pasar solo hacia endpoints que
 * declaran sus roles con {@code @PreAuthorize}. El rol lo verifica después esa
 * anotación; aquí solo se niega lo que no declaró nada.
 */
@Component
public class AutorizacionDeclarada implements AuthorizationManager<RequestAuthorizationContext> {

    private final ObjectProvider<RequestMappingHandlerMapping> mapeo;

    public AutorizacionDeclarada(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mapeo) {
        this.mapeo = mapeo;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> autenticacion, RequestAuthorizationContext contexto) {
        return new AuthorizationDecision(declaraRoles(contexto.getRequest()));
    }

    private boolean declaraRoles(HttpServletRequest peticion) {
        // getHandler necesita la ruta ya parseada y escribe atributos: se hace sobre una copia aislada.
        HttpServletRequest aislada = new PeticionAislada(peticion);
        ServletRequestPathUtils.parseAndCache(aislada);
        try {
            HandlerExecutionChain cadena = mapeo.getObject().getHandler(aislada);
            return cadena != null && cadena.getHandler() instanceof HandlerMethod metodo && declara(metodo);
        } catch (Exception error) {
            // Método no soportado, tipo de contenido no aceptado...: ante la duda se niega.
            return false;
        }
    }

    private static boolean declara(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), PreAuthorize.class);
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
