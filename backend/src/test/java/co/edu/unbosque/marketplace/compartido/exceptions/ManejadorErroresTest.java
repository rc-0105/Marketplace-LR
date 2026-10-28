package co.edu.unbosque.marketplace.compartido.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ManejadorErroresTest {

    private final ManejadorErrores manejador = new ManejadorErrores();

    @Test
    @DisplayName("Las propiedades extra de la excepción se vuelcan al ProblemDetail")
    void volcaLasPropiedadesAlProblema() {
        ProblemDetail problema = manejador.negocio(new ConPropiedades());

        assertThat(problema.getStatus()).isEqualTo(403);
        assertThat(problema.getDetail()).isEqualTo("Detalle");
        assertThat(problema.getProperties()).containsEntry("codigo", "CODIGO_X").containsEntry("motivo", "razon");
    }

    @Test
    @DisplayName("Sin propiedades la respuesta conserva la forma de siempre")
    void sinPropiedadesNoAgregaNada() {
        ProblemDetail problema = manejador.negocio(new SinPropiedades());

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getDetail()).isEqualTo("Sin extras");
        assertThat(problema.getProperties()).isNull();
    }

    @Test
    @DisplayName("Una propiedad con valor nulo falla al construir la excepción")
    void rechazaPropiedadesNulas() {
        Map<String, Object> conNulo = new HashMap<>();
        conNulo.put("motivo", null);

        assertThatThrownBy(() -> new ConNulo(conNulo)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Por HTTP responde application/problem+json con el código y el motivo")
    void respondeProblemJsonConPropiedades() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorFalso()).setControllerAdvice(manejador).build();

        mvc.perform(get("/falso"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CODIGO_X"))
                .andExpect(jsonPath("$.motivo").value("razon"));
    }

    @RestController
    static class ControladorFalso {

        @GetMapping("/falso")
        void lanzar() {
            throw new ConPropiedades();
        }
    }

    static class ConPropiedades extends NegocioException {
        ConPropiedades() {
            super(HttpStatus.FORBIDDEN, "Detalle", Map.of("codigo", "CODIGO_X", "motivo", "razon"));
        }
    }

    static class SinPropiedades extends NegocioException {
        SinPropiedades() {
            super(HttpStatus.CONFLICT, "Sin extras");
        }
    }

    static class ConNulo extends NegocioException {
        ConNulo(Map<String, Object> propiedades) {
            super(HttpStatus.FORBIDDEN, "Detalle", propiedades);
        }
    }
}
