package co.edu.unbosque.marketplace.compartido.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltroCorrelacionTest {

    private final FiltroCorrelacion filtro = new FiltroCorrelacion();
    private final MockHttpServletResponse respuesta = new MockHttpServletResponse();
    private final AtomicReference<String> vistoEnLaCadena = new AtomicReference<>();

    private void filtrar(MockHttpServletRequest peticion) throws Exception {
        filtro.doFilter(peticion, respuesta,
                (req, resp) -> vistoEnLaCadena.set(MDC.get(FiltroCorrelacion.CLAVE_MDC)));
    }

    @Test
    @DisplayName("Sin cabecera genera un identificador, lo devuelve y limpia el MDC al terminar")
    void generaIdentificador() throws Exception {
        filtrar(new MockHttpServletRequest());

        assertThat(UUID.fromString(vistoEnLaCadena.get())).isNotNull();
        assertThat(respuesta.getHeader(FiltroCorrelacion.CABECERA)).isEqualTo(vistoEnLaCadena.get());
        assertThat(MDC.get(FiltroCorrelacion.CLAVE_MDC)).isNull();
    }

    @Test
    @DisplayName("Respeta el identificador que manda el cliente si tiene un formato válido")
    void respetaIdentificadorValido() throws Exception {
        var peticion = new MockHttpServletRequest();
        peticion.addHeader(FiltroCorrelacion.CABECERA, "front-abc-123");

        filtrar(peticion);

        assertThat(vistoEnLaCadena.get()).isEqualTo("front-abc-123");
    }

    @Test
    @DisplayName("Descarta un identificador que podría inyectar líneas falsas en el log")
    void descartaIdentificadorInvalido() throws Exception {
        var peticion = new MockHttpServletRequest();
        peticion.addHeader(FiltroCorrelacion.CABECERA, "abc\n{\"accion\":\"FALSA\"}");

        filtrar(peticion);

        assertThat(UUID.fromString(vistoEnLaCadena.get())).isNotNull();
    }
}
