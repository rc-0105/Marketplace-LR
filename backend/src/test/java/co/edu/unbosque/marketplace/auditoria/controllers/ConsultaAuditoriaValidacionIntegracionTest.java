package co.edu.unbosque.marketplace.auditoria.controllers;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Parámetros inválidos de la lista: 400 como problem+json, sin recortar en silencio (REQ-CA3). */
class ConsultaAuditoriaValidacionIntegracionTest extends AuditoriaHttpIntegracionBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "tamano=201", "tamano=0", "tamano=-1", "tamano=abc", "pagina=-1", "pagina=abc",
            "desde=2026-11-16&hasta=2026-11-15", "desde=2026-13-45", "desde=abc", "hasta=16/11/2026",
            "evento=PAGO", "evento=reserva"})
    @DisplayName("Un parámetro inválido responde 400 application/problem+json con status 400")
    void rechazaParametrosInvalidos(String consulta) throws Exception {
        semilla.sembrarBase();

        consultar(RUTA + "?" + consulta).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("desde igual a hasta es válido y cubre ese día completo")
    void aceptaUnSoloDia() throws Exception {
        semilla.sembrarBase();

        consultar(RUTA + "?desde=2026-11-12&hasta=2026-11-12").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @ParameterizedTest(name = "tamano={0}")
    @ValueSource(ints = {1, 200})
    @DisplayName("Los extremos del tamaño permitido responden 200 y respetan el tamaño")
    void aceptaLosExtremosDelTamano(int tamano) throws Exception {
        semilla.sembrarBase();

        consultar(RUTA + "?tamano=" + tamano).andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(tamano))
                .andExpect(jsonPath("$.contenido.length()").value(Math.min(tamano, 7)));
    }
}
