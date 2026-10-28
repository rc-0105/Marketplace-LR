package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

class AutorizacionDeclaradaTest {

    private final RequestMappingHandlerMapping mapeo = mock(RequestMappingHandlerMapping.class);
    private final AutorizacionDeclarada autorizacion = new AutorizacionDeclarada(proveedor(mapeo));
    private final MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/recurso");

    @Test
    @DisplayName("Concede el paso cuando el método del controlador declara sus roles")
    void concedeConMetodoDeclarado() throws Exception {
        responder(new HandlerExecutionChain(metodo("declarado")));

        assertThat(decidir()).isTrue();
    }

    @Test
    @DisplayName("Concede el paso cuando la clase del controlador declara sus roles")
    void concedeConClaseDeclarada() throws Exception {
        HandlerMethod metodo = new HandlerMethod(new ControladorDeclarado(),
                ControladorDeclarado.class.getDeclaredMethod("operacion"));
        responder(new HandlerExecutionChain(metodo));

        assertThat(decidir()).isTrue();
    }

    @Test
    @DisplayName("Niega el paso cuando el método no declara roles")
    void niegaSinDeclaracion() throws Exception {
        responder(new HandlerExecutionChain(metodo("sinDeclarar")));

        assertThat(decidir()).isFalse();
    }

    @Test
    @DisplayName("Niega el paso cuando ningún controlador atiende la ruta")
    void niegaSinControlador() throws Exception {
        responder(null);

        assertThat(decidir()).isFalse();
    }

    @Test
    @DisplayName("Niega el paso cuando el manejador no es un método de controlador")
    void niegaManejadorQueNoEsMetodo() throws Exception {
        responder(new HandlerExecutionChain(new Object()));

        assertThat(decidir()).isFalse();
    }

    @Test
    @DisplayName("Niega el paso cuando resolver el controlador falla")
    void niegaSiResolverFalla() throws Exception {
        when(mapeo.getHandler(any(HttpServletRequest.class)))
                .thenThrow(new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(decidir()).isFalse();
    }

    @Test
    @DisplayName("No deja en la petición real los atributos que escribe la resolución")
    void noEnsuciaLaPeticion() throws Exception {
        when(mapeo.getHandler(any(HttpServletRequest.class))).thenAnswer(invocacion -> {
            HttpServletRequest aislada = invocacion.getArgument(0);
            assertThat(ServletRequestPathUtils.hasParsedRequestPath(aislada)).isTrue();
            aislada.setAttribute("escrito", true);
            return new HandlerExecutionChain(metodo("declarado"));
        });

        decidir();

        assertThat(ServletRequestPathUtils.hasParsedRequestPath(peticion)).isFalse();
        assertThat(peticion.getAttribute("escrito")).isNull();
    }

    private boolean decidir() {
        Authentication anonimo = mock(Authentication.class);
        return autorizacion.check(() -> anonimo, new RequestAuthorizationContext(peticion)).isGranted();
    }

    private void responder(HandlerExecutionChain cadena) throws Exception {
        when(mapeo.getHandler(any(HttpServletRequest.class))).thenReturn(cadena);
    }

    private static HandlerMethod metodo(String nombre) throws NoSuchMethodException {
        return new HandlerMethod(new ControladorMixto(), ControladorMixto.class.getDeclaredMethod(nombre));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RequestMappingHandlerMapping> proveedor(RequestMappingHandlerMapping mapeo) {
        ObjectProvider<RequestMappingHandlerMapping> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(mapeo);
        return proveedor;
    }

    static class ControladorMixto {

        @PreAuthorize(Permisos.HUESPED)
        void declarado() {
            // Solo se inspecciona su anotación.
        }

        void sinDeclarar() {
            // Solo se inspecciona su anotación.
        }
    }

    @PreAuthorize(Permisos.AUTENTICADO)
    static class ControladorDeclarado {

        void operacion() {
            // Solo se inspecciona la anotación de la clase.
        }
    }
}
