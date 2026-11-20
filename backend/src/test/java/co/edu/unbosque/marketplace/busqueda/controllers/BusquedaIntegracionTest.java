package co.edu.unbosque.marketplace.busqueda.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

/** Búsqueda (SOF-28): token → autorización → servicios de tres módulos → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class BusquedaIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LocalDate.now().plusDays(15);

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
    @MockBean(answer = Answers.CALLS_REAL_METHODS)
    private ConsultaReservasCalendario reservas;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // Los periodos caen en cascada con la publicación, y las publicaciones referencian
    // al usuario: si quedan, los tests de otros módulos no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Devuelve solo las ACTIVAS de la ciudad con un periodo que cubre la estancia, sin distinguir mayúsculas")
    void devuelveLasActivasConDisponibilidad() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long cubierta = publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.plusDays(1), SALIDA.plusDays(5));
        publicacion(anfitrion, "Santa Marta", "BORRADOR", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        publicacion(anfitrion, "Santa Marta", "PAUSADA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        publicacion(anfitrion, "Cartagena", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));

        buscar(iniciarSesion("ana@correo.com"), "santa marta", LLEGADA, SALIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(cubierta.intValue()))
                .andExpect(jsonPath("$[0].ciudad").value("Santa Marta"));
    }

    // CALLS_REAL_METHODS deja vivo el método por lote default del puerto y solo se stubea ocupantesEntre.
    @Test
    @DisplayName("Una publicación cubierta por un periodo pero con una reserva que ocupa la estancia no aparece")
    void excluyeLaCubiertaConUnaReservaOcupante() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long ocupada = publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        Long libre = publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        Long adyacente = publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        doReturn(List.of(new ReservaOcupada(77L, LLEGADA.plusDays(1), SALIDA.minusDays(1))))
                .when(reservas).ocupantesEntre(eq(ocupada), any(), any());
        doReturn(List.of(new ReservaOcupada(78L, LLEGADA.minusDays(2), LLEGADA)))
                .when(reservas).ocupantesEntre(eq(adyacente), any(), any());

        buscar(iniciarSesion("ana@correo.com"), "Santa Marta", LLEGADA, SALIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d)]", ocupada).isEmpty())
                .andExpect(jsonPath("$[?(@.id == %d)]", libre).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == %d)]", adyacente).isNotEmpty());
    }

    @Test
    @DisplayName("Al restringir al anfitrión sus ACTIVAS salen de la búsqueda y al rehabilitarlo vuelven, sin tocar otras")
    void restringirAlAnfitrionOcultaSusPublicaciones() throws Exception {
        Long luis = usuarios.findByCorreo("luis@correo.com").orElseThrow().getId();
        usuarios.save(Usuario.nuevo("Marta Ríos", "marta@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Admin", "admin@correo.com", codificador.encode(CONTRASENA), Rol.ADMINISTRADOR));
        Long deLuis = publicacion(iniciarSesion("luis@correo.com"), "Santa Marta", "ACTIVA", LLEGADA, SALIDA);
        Long deMarta = publicacion(iniciarSesion("marta@correo.com"), "Santa Marta", "ACTIVA", LLEGADA, SALIDA);
        String admin = iniciarSesion("admin@correo.com");
        String ana = iniciarSesion("ana@correo.com");

        mvc.perform(post("/api/v1/admin/usuarios/" + luis + "/restringir")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Fotos engañosas\"}"))
                .andExpect(status().isNoContent());

        buscar(ana, "Santa Marta", LLEGADA, SALIDA)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deMarta.intValue()))
                .andExpect(jsonPath("$[0].anfitrionId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, deLuis))
                .isEqualTo("ACTIVA");

        mvc.perform(post("/api/v1/admin/usuarios/" + luis + "/rehabilitar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNoContent());

        buscar(ana, "Santa Marta", LLEGADA, SALIDA)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(deLuis.intValue()))
                .andExpect(jsonPath("$[1].id").value(deMarta.intValue()));
    }

    @Test
    @DisplayName("Si ninguna publicación cumple responde 200 con una lista vacía")
    void sinResultadosRespondeListaVacia() throws Exception {
        buscar(iniciarSesion("ana@correo.com"), "Leticia", LLEGADA, SALIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Si la salida no es posterior a la llegada responde 400 y no busca")
    void rechazaSalidaNoPosterior() throws Exception {
        buscar(iniciarSesion("ana@correo.com"), "Santa Marta", SALIDA, LLEGADA)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Solo el huésped busca: el anfitrión recibe 403 y sin token 401")
    void soloElHuespedBusca() throws Exception {
        buscar(iniciarSesion("luis@correo.com"), "Santa Marta", LLEGADA, SALIDA)
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/busqueda").param("ciudad", "Santa Marta").param("llegada", LLEGADA.toString())
                .param("salida", SALIDA.toString())).andExpect(status().isUnauthorized());
    }

    private ResultActions buscar(String token, String ciudad, LocalDate llegada, LocalDate salida) throws Exception {
        return mvc.perform(get("/api/v1/busqueda").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("ciudad", ciudad).param("llegada", llegada.toString()).param("salida", salida.toString()));
    }

    // Crea la publicación por la API y fija ciudad, estado y periodo directo: ACTIVA solo se
    // alcanza con la aprobación del administrador, que no es lo que se prueba aquí.
    private Long publicacion(String token, String ciudad, String estado, LocalDate inicio, LocalDate fin)
            throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET ciudad = ?, estado = ? WHERE id = ?", ciudad, estado, id);
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)", id,
                java.sql.Date.valueOf(inicio), java.sql.Date.valueOf(fin));
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
