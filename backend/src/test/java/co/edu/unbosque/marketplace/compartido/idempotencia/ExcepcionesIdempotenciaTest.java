package co.edu.unbosque.marketplace.compartido.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.compartido.exceptions.ManejadorErrores;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaInvalidaException;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaReutilizadaException;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.SolicitudEnProcesoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Las tres excepciones de idempotencia y su traducción por el manejador único (REQ-IS7, REQ-IS13). */
class ExcepcionesIdempotenciaTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorFalso())
            .setControllerAdvice(new ManejadorErrores()).build();

    @Test
    @DisplayName("Una clave inválida responde 400 problem+json con su propio código")
    void claveInvalida() throws Exception {
        mvc.perform(get("/falso/invalida"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_INVALIDA"));
    }

    @Test
    @DisplayName("Una clave reutilizada con otro cuerpo responde 422 problem+json")
    void claveReutilizada() throws Exception {
        mvc.perform(get("/falso/reutilizada"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));
    }

    @Test
    @DisplayName("Una solicitud en proceso responde 409 problem+json con Retry-After y un código que no es RESERVADO")
    void solicitudEnProceso() throws Exception {
        mvc.perform(get("/falso/proceso"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_EN_PROCESO"))
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    @DisplayName("El Retry-After es el entero de segundos con que se construyó la excepción")
    void retryAfterSigueLaExcepcion() {
        ResponseEntity<?> respuesta = new ManejadorErrores().solicitudEnProceso(new SolicitudEnProcesoException(5));

        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }

    @RestController
    static class ControladorFalso {

        @GetMapping("/falso/invalida")
        void invalida() {
            throw new ClaveIdempotenciaInvalidaException();
        }

        @GetMapping("/falso/reutilizada")
        void reutilizada() {
            throw new ClaveIdempotenciaReutilizadaException();
        }

        @GetMapping("/falso/proceso")
        void proceso() {
            throw new SolicitudEnProcesoException(2);
        }
    }
}
