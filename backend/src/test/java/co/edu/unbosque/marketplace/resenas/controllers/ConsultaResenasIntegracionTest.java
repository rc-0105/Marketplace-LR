package co.edu.unbosque.marketplace.resenas.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consulta de las reseñas y la calificación promedio (SOF-81, HU-36): token → autorización → service →
 * PostgreSQL, igual que ResenaIntegracionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsultaResenasIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LLEGADA.plusDays(4);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private ObjectMapper json;

    private Long publicacionId;

    @BeforeEach
    void preparar() throws Exception {
        limpiarDatos();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Carla Ruiz", "carla@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        publicacionId = publicacionActiva();
    }

    @AfterEach
    void limpiar() {
        limpiarDatos();
    }

    private void limpiarDatos() {
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM cancelacion");
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("AC1: el promedio lleva un decimal y las reseñas visibles van de la más reciente a la más antigua")
    void promedioYOrden() throws Exception {
        long reservaAna = resenaDe("ana@correo.com", 4, "Muy cómodo", "2026-01-10T10:00:00Z");
        long reservaCarla = resenaDe("carla@correo.com", 5, "Perfecto", "2026-02-10T10:00:00Z");
        String token = iniciarSesion("ana@correo.com");

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio").value(4.5))
                .andExpect(jsonPath("$.resenas.length()").value(2))
                .andExpect(jsonPath("$.resenas[0].reservaId").value(reservaCarla))
                .andExpect(jsonPath("$.resenas[1].reservaId").value(reservaAna));
    }

    @Test
    @DisplayName("AC3: cada reseña muestra el autor, la fecha, el contenido y la reserva que le dio origen")
    void cadaResenaTraeAutorFechaContenidoYReserva() throws Exception {
        long reservaAna = resenaDe("ana@correo.com", 4, "Muy cómodo", "2026-01-10T10:00:00Z");
        String token = iniciarSesion("carla@correo.com");

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas[0].autor").value("Ana Gómez"))
                .andExpect(jsonPath("$.resenas[0].contenido").value("Muy cómodo"))
                .andExpect(jsonPath("$.resenas[0].fecha").value("2026-01-10T10:00:00Z"))
                .andExpect(jsonPath("$.resenas[0].reservaId").value(reservaAna))
                .andExpect(jsonPath("$.resenas[0].calificacion").value(4));
    }

    @Test
    @DisplayName("AC2: sin reseñas el promedio queda nulo (no cero) y la lista vacía")
    void sinResenas() throws Exception {
        String token = iniciarSesion("ana@correo.com");

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio").isEmpty())
                .andExpect(jsonPath("$.resenas").isEmpty());
    }

    @Test
    @DisplayName("Una reseña oculta por la moderación no cuenta ni en la lista ni en el promedio")
    void laOcultaNoCuenta() throws Exception {
        long reservaAna = resenaDe("ana@correo.com", 4, "Muy cómodo", "2026-01-10T10:00:00Z");
        resenaDe("carla@correo.com", 2, "Regular", "2026-02-10T10:00:00Z");
        jdbc.update("UPDATE resena SET estado = 'OCULTA' WHERE calificacion = 2");
        String token = iniciarSesion("ana@correo.com");

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio").value(4.0))
                .andExpect(jsonPath("$.resenas.length()").value(1))
                .andExpect(jsonPath("$.resenas[0].reservaId").value(reservaAna));
    }

    @Test
    @DisplayName("AC1: el detalle de la publicación trae el promedio de las reseñas visibles")
    void elDetalleTraeElPromedio() throws Exception {
        resenaDe("ana@correo.com", 4, "Muy cómodo", "2026-01-10T10:00:00Z");
        resenaDe("carla@correo.com", 5, "Perfecto", "2026-02-10T10:00:00Z");
        String token = iniciarSesion("ana@correo.com");

        mvc.perform(get("/api/v1/busqueda/{id}", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").value(4.5));
    }

    @Test
    @DisplayName("Sin token responde 401 y un anfitrión responde 403")
    void exigeSerHuesped() throws Exception {
        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com")))
                .andExpect(status().isForbidden());
    }

    // Crea la reserva COMPLETADA del huésped, la reseña por la API y fija su fecha para que el orden sea fijo.
    private long resenaDe(String correo, int calificacion, String contenido, String fecha) throws Exception {
        String token = iniciarSesion(correo);
        long reservaId = reservaCompletada(token);
        mvc.perform(post("/api/v1/reservas/{id}/resena", reservaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"calificacion": %d, "contenido": "%s"}
                                """.formatted(calificacion, contenido)))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE resena SET fecha = ?::timestamptz WHERE reserva_id = ?", fecha, reservaId);
        return reservaId;
    }

    // El cierre automático (SOF-78) pasa a COMPLETADA las CONFIRMADA con la estancia terminada; aquí se
    // reproduce su resultado directamente para no depender del reloj de la tarea programada.
    private long reservaCompletada(String token) throws Exception {
        long id = reservarYPagar(token);
        jdbc.update("UPDATE reserva SET llegada = ?, salida = ?, estado = 'COMPLETADA' WHERE id = ?",
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(6), id);
        return id;
    }

    private long reservarYPagar(String token) throws Exception {
        long id = reservar(token);
        mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"metodoPago": "pm_card_visa"}
                                """))
                .andExpect(status().isOk());
        return id;
    }

    private long reservar(String token) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": []}
                                """.formatted(publicacionId, LLEGADA, SALIDA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    // Igual que ResenaIntegracionTest: ACTIVA con precio fijo y un periodo que cubre las fechas.
    private Long publicacionActiva() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = 'ACTIVA', precio_noche = 100000, tarifa_limpieza = 30000 "
                + "WHERE id = ?", id);
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)",
                id, LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
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
