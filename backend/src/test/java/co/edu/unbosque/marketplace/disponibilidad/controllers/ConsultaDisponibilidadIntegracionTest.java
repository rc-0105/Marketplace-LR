package co.edu.unbosque.marketplace.disponibilidad.controllers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Consulta de disponibilidad (SOF-27): token, rol, servicio y PostgreSQL, con el puerto de reservas simulado. */
@SpringBootTest
@AutoConfigureMockMvc
class ConsultaDisponibilidadIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String ESTANCIA = "desde=2026-12-03&hasta=2026-12-06";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private ObjectMapper json;

    // CALLS_REAL_METHODS deja vivo el método por lote default del puerto: solo se stubea ocupantesEntre.
    @MockBean(answer = Answers.CALLS_REAL_METHODS)
    private ConsultaReservasCalendario reservas;

    private String duenoToken;
    private String huespedToken;
    private Long publicacionId;

    @BeforeEach
    void preparar() throws Exception {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Admin", "admin@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));
        duenoToken = iniciarSesion("luis@correo.com");
        huespedToken = iniciarSesion("ana@correo.com");
        publicacionId = publicacion(duenoToken, "ACTIVA");
    }

    // Los periodos caen en cascada con la publicación, y las publicaciones referencian
    // al usuario: si quedan, los tests de otros módulos no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Sin token responde 401 y un administrador 403, sin consultar reservas")
    void sinTokenYAdministrador() throws Exception {
        mvc.perform(get(ruta(publicacionId, ESTANCIA))).andExpect(status().isUnauthorized());
        consultar(iniciarSesion("admin@correo.com"), publicacionId, ESTANCIA).andExpect(status().isForbidden());

        verifyNoInteractions(reservas);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Una consulta sin fechas, mal formada o que termina antes de empezar responde 400 sin consultar reservas")
    @ValueSource(strings = {"hasta=2026-12-06", "desde=2026-12-03", "desde=2026-12-03&hasta=2026-12-03",
            "desde=2026-12-06&hasta=2026-12-03", "desde=2026-13-45&hasta=2026-12-06", "desde=abc&hasta=2026-12-06"})
    void parametrosInvalidosResponden400(String consulta) throws Exception {
        consultar(huespedToken, publicacionId, consulta).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Una fecha faltante indica el campo en errores y un id no numérico responde 400")
    void faltanteIndicaElCampoEIdNoNumerico() throws Exception {
        consultar(huespedToken, publicacionId, "hasta=2026-12-06").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.desde").value("Indica la fecha inicial"));
        mvc.perform(get("/api/v1/publicaciones/abc/disponibilidad/consulta?" + ESTANCIA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + huespedToken)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Inexistente y BORRADOR dan el mismo 404, y el 404 va antes que un rango inválido")
    void elNoEncontradoEsIndistinguible() throws Exception {
        Long borrador = publicacion(duenoToken, "BORRADOR");

        JsonNode noActiva = json.readTree(consultar(huespedToken, borrador, ESTANCIA).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(UTF_8));
        JsonNode inexistente = json.readTree(consultar(huespedToken, 999_999L, ESTANCIA)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(UTF_8));
        consultar(huespedToken, 999_999L, "desde=2026-12-06&hasta=2026-12-03").andExpect(status().isNotFound());

        assertThat(noActiva.get("status")).isEqualTo(inexistente.get("status"));
        assertThat(noActiva.get("title")).isEqualTo(inexistente.get("title"));
        assertThat(noActiva.get("detail")).isEqualTo(inexistente.get("detail"));
        assertThat(noActiva.get("detail").asText()).isEqualTo("No encontramos esa publicación.");
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Un huésped y otro anfitrión consultan una ACTIVA y el dueño una en BORRADOR")
    void quienPuedeConsultar() throws Exception {
        periodo(publicacionId, "2026-12-01", "2026-12-10");
        Long borrador = publicacion(duenoToken, "BORRADOR");
        periodo(borrador, "2026-12-01", "2026-12-10");

        consultar(huespedToken, publicacionId, ESTANCIA).andExpect(status().isOk());
        consultar(iniciarSesion("marta@correo.com"), publicacionId, ESTANCIA).andExpect(status().isOk());
        consultar(duenoToken, borrador, ESTANCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    @DisplayName("Una estancia dentro del periodo responde disponible con el motivo null presente y sin datos de reserva")
    void disponibleTraeMotivoNull() throws Exception {
        periodo(publicacionId, "2026-12-01", "2026-12-10");

        String cuerpo = consultar(huespedToken, publicacionId, ESTANCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicacionId").value(publicacionId.intValue()))
                .andExpect(jsonPath("$.desde").value("2026-12-03"))
                .andExpect(jsonPath("$.hasta").value("2026-12-06"))
                .andExpect(jsonPath("$.disponible").value(true))
                .andReturn().getResponse().getContentAsString();

        List<String> campos = new ArrayList<>();
        json.readTree(cuerpo).fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("publicacionId", "desde", "hasta", "disponible", "motivo");
        assertThat(json.readTree(cuerpo).get("motivo").isNull()).isTrue();
    }

    @Test
    @DisplayName("Fuera del periodo responde FUERA_DE_PERIODO sin consultar reservas, aunque haya una")
    void fueraDePeriodo() throws Exception {
        periodo(publicacionId, "2026-12-01", "2026-12-10");
        doReturn(List.of(new ReservaOcupada(77L, LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 11))))
                .when(reservas).ocupantesEntre(any(), any(), any());

        consultar(huespedToken, publicacionId, "desde=2026-12-08&hasta=2026-12-11").andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.motivo").value("FUERA_DE_PERIODO"));

        verify(reservas, never()).ocupantesEntre(any(), any(), any());
    }

    @Test
    @DisplayName("Con una reserva que cruza responde RESERVADO sin reservaId ni para el dueño, y liberarla lo deja disponible")
    void reservadoYLiberacion() throws Exception {
        periodo(publicacionId, "2026-12-01", "2026-12-10");
        doReturn(List.of(new ReservaOcupada(77L, LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 8))))
                .when(reservas).ocupantesEntre(any(), any(), any());

        String cuerpo = consultar(duenoToken, publicacionId, ESTANCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.motivo").value("RESERVADO"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("reservaId");

        doReturn(List.of()).when(reservas).ocupantesEntre(any(), any(), any());
        consultar(duenoToken, publicacionId, ESTANCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    @DisplayName("Un usuarioId en la query se ignora y consultar no escribe filas")
    void ignoraElUsuarioIdYNoEscribe() throws Exception {
        periodo(publicacionId, "2026-12-01", "2026-12-10");
        Long borrador = publicacion(duenoToken, "BORRADOR");
        Long duenoId = jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", Long.class, "luis@correo.com");
        long publicacionesAntes = contar("publicacion");
        long periodosAntes = contar("periodo_disponible");

        consultar(huespedToken, borrador, ESTANCIA + "&usuarioId=" + duenoId).andExpect(status().isNotFound());
        consultar(duenoToken, publicacionId, ESTANCIA).andExpect(status().isOk());

        assertThat(contar("publicacion")).isEqualTo(publicacionesAntes);
        assertThat(contar("periodo_disponible")).isEqualTo(periodosAntes);
    }

    private void periodo(Long id, String inicio, String fin) {
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)", id,
                java.sql.Date.valueOf(inicio), java.sql.Date.valueOf(fin));
    }

    private long contar(String tabla) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class);
        return total == null ? 0 : total;
    }

    private static String ruta(Long id, String consulta) {
        return "/api/v1/publicaciones/" + id + "/disponibilidad/consulta?" + consulta;
    }

    private ResultActions consultar(String token, Long id, String consulta) throws Exception {
        return mvc.perform(get(ruta(id, consulta)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // Crea la publicación por la API y fija el estado directo: ACTIVA solo se alcanza con la
    // aprobación del administrador, que no es lo que se prueba aquí.
    private Long publicacion(String token, String estado) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = ? WHERE id = ?", estado, id);
        return id;
    }

    private String iniciarSesion(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
