package co.edu.unbosque.marketplace.auditoria.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.ResultActions;

/** La lista de auditoría por HTTP: token, autorización, consulta y forma de la respuesta (REQ-CA1, CA2, CA4). */
class ConsultaAuditoriaIntegracionTest extends AuditoriaHttpIntegracionBase {

    @Test
    @DisplayName("Sin filtros devuelve la página 0 de tamaño 50 con las 7 filas de la más nueva a la más vieja")
    void listaSinFiltros() throws Exception {
        semilla.sembrarBase();

        listar("").andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(50))
                .andExpect(jsonPath("$.totalElementos").value(7))
                .andExpect(jsonPath("$.totalPaginas").value(1));
        assertThat(ids(listarJson(""))).containsExactly(7, 6, 5, 4, 3, 2, 1);
    }

    @Test
    @DisplayName("Cada elemento trae exactamente los campos del DTO y la fecha en ISO con zona")
    void camposDelDto() throws Exception {
        semilla.sembrarBase();

        JsonNode primero = listarJson("entidadId=55&tamano=50").get("contenido").get(3);

        List<String> campos = new ArrayList<>();
        primero.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("id", "fecha", "usuario", "accion", "entidad", "entidadId",
                "resultado", "datos", "correlacion");
        assertThat(primero.get("fecha").asText()).isEqualTo("2026-11-10T15:00:00Z");
        assertThat(primero.get("usuario").asText()).isEqualTo("2");
        assertThat(primero.get("accion").asText()).isEqualTo("RESERVA");
        assertThat(primero.get("entidadId").asText()).isEqualTo("55");
        assertThat(primero.get("resultado").asText()).isEqualTo("EXITO");
        assertThat(primero.get("datos").get("operacion").asText()).isEqualTo("CREAR");
        assertThat(primero.get("correlacion").asText()).isEqualTo("c-1");
    }

    @Test
    @DisplayName("Con la tabla vacía responde 200 con contenido vacío y totales en 0")
    void tablaVacia() throws Exception {
        listar("").andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "tamano y pagina   | tamano=3&pagina=1 | 4 3 2 | 7 | 3",
            "ultima pagina     | tamano=3&pagina=2 | 1 | 7 | 3",
            "pagina fuera      | pagina=5 | - | 7 | 1",
            "AND               | usuario=2&evento=RESERVA&entidad=Reserva&entidadId=55&desde=2026-11-10&hasta=2026-11-11 | 2 1 | 2 | 1",
            "AND sin filas     | usuario=2&evento=RESERVA&entidad=Reserva&entidadId=77&desde=2026-11-10&hasta=2026-11-11 | - | 0 | 0",
            "AND que excluye   | usuario=1&evento=RESERVA | - | 0 | 0",
            "sin coincidencias | usuario=999 | - | 0 | 0",
            "entidad minuscula | entidad=reserva | - | 0 | 0",
            "vacios            | usuario=&evento=&entidad= | 7 6 5 4 3 2 1 | 7 | 1",
            "usuario 2         | usuario=2 | 7 4 3 2 1 | 5 | 1",
            "filtro y paginas  | entidadId=55&tamano=3 | 4 3 2 | 4 | 2"})
    @DisplayName("Los filtros y la paginación llegan por HTTP y responden filas y totales esperados")
    void filtrosPorHttp(String caso, String consulta, String esperado, long total, int paginas) throws Exception {
        semilla.sembrarBase();

        JsonNode respuesta = listarJson(consulta);

        assertThat(ids(respuesta)).isEqualTo(esperado == null ? List.of() : idsEsperados(esperado));
        assertThat(respuesta.get("totalElementos").asLong()).isEqualTo(total);
        assertThat(respuesta.get("totalPaginas").asInt()).isEqualTo(paginas);
    }

    @Test
    @DisplayName("La fila con usuario nulo aparece sin filtro y se serializa con null")
    void usuarioNulo() throws Exception {
        semilla.sembrarBase();

        JsonNode a6 = listarJson("").get("contenido").get(1);

        assertThat(a6.get("usuario").isNull()).isTrue();
        assertThat(a6.get("entidadId").isNull()).isTrue();
        assertThat(a6.get("correlacion").isNull()).isTrue();
    }

    @Test
    @DisplayName("La respuesta es application/json")
    void tipoDeContenido() throws Exception {
        listar("").andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    private ResultActions listar(String consulta) throws Exception {
        return consultar(RUTA + "?" + consulta);
    }

    private JsonNode listarJson(String consulta) throws Exception {
        return json.readTree(listar(consulta).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static List<Integer> ids(JsonNode pagina) {
        List<Integer> ids = new ArrayList<>();
        pagina.get("contenido").forEach(fila -> ids.add(fila.get("id").asInt()));
        return ids;
    }

    private static List<Integer> idsEsperados(String texto) {
        return Arrays.stream(texto.split(" ")).map(Integer::valueOf).toList();
    }
}
