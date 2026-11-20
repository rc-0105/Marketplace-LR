package co.edu.unbosque.marketplace.disponibilidad.controllers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** Calendario (SOF-26): token → autorización por rol → servicio → PostgreSQL, con el puerto de reservas simulado. */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarioIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String VENTANA = "desde=2026-12-01&hasta=2026-12-10";

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

    // Las reservas llegan con SOF-33: aquí se simula la ocupante que ocupa días.
    @MockBean
    private ConsultaReservasCalendario reservas;

    // ConsultaOcupacionReservas (SOF-33) implementa los dos puertos: al reemplazarla por el doble del calendario,
    // el de periodos también hay que darlo, o disponibilidad no arranca.
    @MockBean
    private ConsultaReservasPeriodo reservasPeriodo;

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
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)",
                publicacionId, java.sql.Date.valueOf("2026-12-01"), java.sql.Date.valueOf("2026-12-10"));
        when(reservas.ocupantesEntre(any(), any(), any())).thenReturn(
                List.of(new ReservaOcupada(77L, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 6))));
    }

    // Los periodos caen en cascada con la publicación, y las publicaciones referencian
    // al usuario: si quedan, los tests de otros módulos no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Sin token responde 401 como problem+json")
    void sinTokenResponde401() throws Exception {
        mvc.perform(get(ruta(publicacionId, VENTANA)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Un usuarioId inventado en la query se ignora: el dueño lo decide el token")
    void elUsuarioIdDeLaQuerySeIgnora() throws Exception {
        Long duenoId = jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", Long.class, "luis@correo.com");

        String cuerpo = consultar(huespedToken, publicacionId,
                VENTANA + "&usuarioId=" + duenoId + "&propietarioId=" + duenoId + "&esDueno=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[2].estado").value("RESERVADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("reservaId");
    }

    @Test
    @DisplayName("Un administrador recibe 403 aunque la publicación esté ACTIVA")
    void administradorRecibe403() throws Exception {
        consultar(iniciarSesion("admin@correo.com"), publicacionId, VENTANA).andExpect(status().isForbidden());

        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("El dueño ve cada día reservado con su reservaId y los demás días sin esa propiedad")
    void duenoVeElReservaId() throws Exception {
        consultar(duenoToken, publicacionId, VENTANA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicacionId").value(publicacionId.intValue()))
                .andExpect(jsonPath("$.desde").value("2026-12-01"))
                .andExpect(jsonPath("$.hasta").value("2026-12-10"))
                .andExpect(jsonPath("$.tienePeriodos").value(true))
                .andExpect(jsonPath("$.dias.length()").value(9))
                .andExpect(jsonPath("$.dias[1].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.dias[1].reservaId").doesNotExist())
                .andExpect(jsonPath("$.dias[2].estado").value("RESERVADO"))
                .andExpect(jsonPath("$.dias[2].reservaId").value(77))
                .andExpect(jsonPath("$.dias[4].reservaId").value(77))
                .andExpect(jsonPath("$.dias[5].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.dias[5].reservaId").doesNotExist());
    }

    @Test
    @DisplayName("El huésped ve los días reservados y el JSON crudo no contiene la propiedad reservaId")
    void huespedNoVeElReservaId() throws Exception {
        String cuerpo = consultar(huespedToken, publicacionId, VENTANA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[2].estado").value("RESERVADO"))
                .andExpect(jsonPath("$.dias[4].estado").value("RESERVADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("\"RESERVADO\"").doesNotContain("reservaId");
    }

    @Test
    @DisplayName("Otro anfitrión que no es el dueño recibe la vista pública, sin reservaId")
    void otroAnfitrionRecibeLaVistaPublica() throws Exception {
        String cuerpo = consultar(iniciarSesion("marta@correo.com"), publicacionId, VENTANA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[2].estado").value("RESERVADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("reservaId");
    }

    @Test
    @DisplayName("Una publicación en BORRADOR responde 404 al huésped sin consultar reservas y 200 al dueño")
    void borradorEs404ParaElHuespedYVisibleParaElDueno() throws Exception {
        Long borrador = publicacion(duenoToken, "BORRADOR");

        consultar(huespedToken, borrador, VENTANA).andExpect(status().isNotFound());
        verifyNoInteractions(reservas);
        consultar(duenoToken, borrador, VENTANA).andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(9));
    }

    @Test
    @DisplayName("Una publicación inexistente y una en BORRADOR dan el mismo 404, sin revelar que existe")
    void elCuatrocientosCuatroEsIndistinguible() throws Exception {
        Long borrador = publicacion(duenoToken, "BORRADOR");

        JsonNode noActiva = json.readTree(consultar(huespedToken, borrador, VENTANA).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(UTF_8));
        JsonNode inexistente = json.readTree(consultar(huespedToken, 999_999L, VENTANA)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(UTF_8));

        // "instance" es la ruta pedida y cambia con el id; el resto de la respuesta no puede distinguirlos.
        assertThat(noActiva.get("status")).isEqualTo(inexistente.get("status"));
        assertThat(noActiva.get("title")).isEqualTo(inexistente.get("title"));
        assertThat(noActiva.get("detail")).isEqualTo(inexistente.get("detail"));
        assertThat(noActiva.get("detail").asText()).isEqualTo("No encontramos esa publicación.");
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Un rango inválido, una fecha mal formada o un id no numérico responden 400 sin consultar reservas")
    @ValueSource(strings = {"desde=2026-12-10&hasta=2026-12-09", "desde=2026-01-01&hasta=2027-01-03",
            "desde=2026-13-45", "desde=abc", "hasta=2026-02-30"})
    void parametrosInvalidosResponden400(String consulta) throws Exception {
        consultar(huespedToken, publicacionId, consulta).andExpect(status().isBadRequest());

        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Un rango fuera de límites responde 400 como problem+json con el motivo")
    void rangoFueraDeLimitesTraeElMotivo() throws Exception {
        consultar(huespedToken, publicacionId, "desde=2026-01-01&hasta=2027-01-03")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El calendario no puede abarcar más de 366 días."));
    }

    @Test
    @DisplayName("Una fecha mal formada responde 400 con el campo en errores y no 500")
    void fechaMalFormadaResponde400ConElCampo() throws Exception {
        consultar(huespedToken, publicacionId, "desde=2026-13-45")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("La solicitud tiene datos inválidos."))
                .andExpect(jsonPath("$.errores.desde").value("El valor no tiene el formato esperado (por ejemplo, 2027-01-15)."));
    }

    @Test
    @DisplayName("Un id de publicación no numérico responde 400 sin consultar reservas")
    void idNoNumericoResponde400() throws Exception {
        mvc.perform(get("/api/v1/publicaciones/abc/calendario?" + VENTANA).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + huespedToken)).andExpect(status().isBadRequest());

        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Sin parámetros usa la ventana de 12 meses desde hoy en Bogotá y tienePeriodos sigue en true")
    void sinParametrosUsaLaVentanaPorDefecto() throws Exception {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        consultar(huespedToken, publicacionId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(hoy.toString()))
                .andExpect(jsonPath("$.hasta").value(hoy.plusMonths(12).toString()))
                .andExpect(jsonPath("$.tienePeriodos").value(true));
    }

    @Test
    @DisplayName("Una ventana sin periodos da todos los días BLOQUEADO y tienePeriodos sigue en true")
    void tienePeriodosNoDependeDeLaVentana() throws Exception {
        when(reservas.ocupantesEntre(any(), any(), any())).thenReturn(List.of());

        consultar(huespedToken, publicacionId, "desde=2027-03-01&hasta=2027-03-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tienePeriodos").value(true))
                .andExpect(jsonPath("$.dias.length()").value(4))
                .andExpect(jsonPath("$.dias[0].estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.dias[3].estado").value("BLOQUEADO"));
    }

    @Test
    @DisplayName("Consultar el calendario no escribe filas en la base de datos")
    void consultarNoEscribe() throws Exception {
        long publicacionesAntes = contar("publicacion");
        long periodosAntes = contar("periodo_disponible");

        consultar(duenoToken, publicacionId, VENTANA).andExpect(status().isOk());
        consultar(huespedToken, 999_999L, VENTANA).andExpect(status().isNotFound());
        consultar(huespedToken, publicacionId, "desde=abc").andExpect(status().isBadRequest());

        assertThat(contar("publicacion")).isEqualTo(publicacionesAntes);
        assertThat(contar("periodo_disponible")).isEqualTo(periodosAntes);
    }

    private long contar(String tabla) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class);
        return total == null ? 0 : total;
    }

    private static String ruta(Long id, String consulta) {
        return "/api/v1/publicaciones/" + id + "/calendario" + (consulta.isEmpty() ? "" : "?" + consulta);
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
