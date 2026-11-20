package co.edu.unbosque.marketplace.auditoria.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** El detalle de un evento por HTTP (REQ-CA6). */
class ConsultaAuditoriaDetalleIntegracionTest extends AuditoriaHttpIntegracionBase {

    @Test
    @DisplayName("El detalle de un evento sin usuario, entidadId ni correlación los devuelve nulos")
    void detalleConNulos() throws Exception {
        semilla.sembrarBase();

        consultar(RUTA + "/6").andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(nullValue()))
                .andExpect(jsonPath("$.entidadId").value(nullValue()))
                .andExpect(jsonPath("$.correlacion").value(nullValue()))
                .andExpect(jsonPath("$.resultado").value("FALLO"));
    }

    @Test
    @DisplayName("Un id inexistente responde 404 application/problem+json")
    void idInexistente() throws Exception {
        consultar(RUTA + "/999999").andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No encontramos ese evento de auditoría."));
    }

    @Test
    @DisplayName("Un id no numérico responde 400")
    void idNoNumerico() throws Exception {
        consultar(RUTA + "/abc").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El detalle coincide campo a campo con el elemento de la lista")
    void detalleIgualALista() throws Exception {
        semilla.sembrarBase();
        JsonNode lista = json.readTree(consultar(RUTA + "?entidadId=55").andReturn().getResponse()
                .getContentAsString());
        JsonNode detalle = json.readTree(consultar(RUTA + "/1").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());

        assertThat(detalle).isEqualTo(lista.get("contenido").get(3));
    }
}
