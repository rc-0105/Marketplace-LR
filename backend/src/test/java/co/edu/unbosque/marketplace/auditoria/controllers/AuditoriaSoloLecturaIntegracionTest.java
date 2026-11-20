package co.edu.unbosque.marketplace.auditoria.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** La API solo lee: la secuencia por reserva, ningún verbo de escritura y ninguna auditoría de la consulta. */
// Pool chico: el SpyBean crea un contexto propio y PostgreSQL limita las conexiones totales.
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=3")
class AuditoriaSoloLecturaIntegracionTest extends AuditoriaHttpIntegracionBase {

    @SpyBean
    private PublicadorEventos publicador;

    @Test
    @DisplayName("La reserva 55 reconstruye su secuencia, la 77 queda aislada y A3 y A4 comparten correlación")
    void secuenciaPorReserva() throws Exception {
        semilla.sembrarBase();

        List<JsonNode> reserva55 = eventos("entidad=Reserva&entidadId=55");
        Collections.reverse(reserva55);

        assertThat(reserva55).extracting(e -> e.get("datos").get("operacion").asText())
                .containsExactly("CREAR", "PAGAR", "CANCELAR", "DEVOLVER");
        assertThat(reserva55).extracting(e -> e.get("accion").asText())
                .containsExactly("RESERVA", "RESERVA", "CANCELACION", "CANCELACION");
        assertThat(reserva55).extracting(e -> e.get("correlacion").asText())
                .containsExactly("c-1", "c-2", "c-3", "c-3");
        assertThat(eventos("entidad=Reserva&entidadId=77")).extracting(e -> e.get("id").asInt())
                .containsExactly(7);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/v1/admin/auditoria", "PUT, /api/v1/admin/auditoria", "PATCH, /api/v1/admin/auditoria",
            "DELETE, /api/v1/admin/auditoria", "POST, /api/v1/admin/auditoria/1", "PUT, /api/v1/admin/auditoria/1",
            "PATCH, /api/v1/admin/auditoria/1", "DELETE, /api/v1/admin/auditoria/1"})
    @DisplayName("Los verbos de escritura responden 403 incluso al administrador y no cambian las filas")
    void rechazaLaEscritura(String metodo, String ruta) throws Exception {
        semilla.sembrarBase();

        mvc.perform(request(HttpMethod.valueOf(metodo), ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        assertThat(semilla.contar()).isEqualTo(7);
    }

    @Test
    @DisplayName("Consultar no publica eventos de auditoría ni cambia el conteo de filas")
    void consultarNoAudita() throws Exception {
        semilla.sembrarBase();
        clearInvocations(publicador);

        consultar(RUTA).andExpect(status().isOk());
        consultar(RUTA + "/1").andExpect(status().isOk());

        verifyNoInteractions(publicador);
        assertThat(semilla.contar()).isEqualTo(7);
    }

    private List<JsonNode> eventos(String consulta) throws Exception {
        JsonNode pagina = json.readTree(consultar(RUTA + "?" + consulta).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        List<JsonNode> filas = new ArrayList<>();
        pagina.get("contenido").forEach(filas::add);
        return filas;
    }
}
