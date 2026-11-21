package co.edu.unbosque.marketplace.resenas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Moderación de reseñas inapropiadas (SOF-82, HU-37): token → autorización → service → PostgreSQL, igual que
 * ConsultaResenasIntegracionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModeracionResenasIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String MOTIVO = "Lenguaje ofensivo contra el anfitrión";
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
        usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));
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
    @DisplayName("AC1: la reseña oculta deja de mostrarse a los huéspedes y de contar en el promedio")
    void laOcultaNoSeMuestraNiCuenta() throws Exception {
        long resenaAna = resenaDe("ana@correo.com", 5, "Perfecto");
        resenaDe("carla@correo.com", 1, "Pésimo y ofensivo");
        long resenaCarla = idDeResena("carla@correo.com");

        ocultar(iniciarSesion("admin@correo.com"), resenaCarla, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OCULTA"))
                .andExpect(jsonPath("$.motivo").value(MOTIVO));

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio").value(5.0))
                .andExpect(jsonPath("$.resenas.length()").value(1))
                .andExpect(jsonPath("$.resenas[0].reservaId").value(resenaAna));
    }

    @Test
    @DisplayName("AC2: la reseña oculta sigue existiendo con su contenido original")
    void laFilaSeConserva() throws Exception {
        resenaDe("ana@correo.com", 1, "Contenido original");
        long resenaId = idDeResena("ana@correo.com");

        ocultar(iniciarSesion("admin@correo.com"), resenaId, MOTIVO).andExpect(status().isOk());

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT contenido, calificacion, estado, motivo_ocultacion FROM resena WHERE id = ?", resenaId);
        assertThat(fila.get("contenido")).isEqualTo("Contenido original");
        assertThat(fila.get("calificacion")).isEqualTo(1);
        assertThat(fila.get("estado")).isEqualTo("OCULTA");
        assertThat(fila.get("motivo_ocultacion")).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("AC3: queda un evento de auditoría con el administrador, la reseña, la fecha y el motivo")
    void quedaAuditada() throws Exception {
        resenaDe("ana@correo.com", 1, "Ofensiva");
        long resenaId = idDeResena("ana@correo.com");
        Long administradorId = jdbc.queryForObject("SELECT id FROM usuario WHERE correo = 'admin@correo.com'",
                Long.class);

        ocultar(iniciarSesion("admin@correo.com"), resenaId, MOTIVO).andExpect(status().isOk());

        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT usuario, fecha, datos::text AS datos FROM evento_auditoria "
                        + "WHERE accion = 'ACCION_ADMINISTRATIVA' AND entidad = 'Resena' AND entidad_id = ?",
                String.valueOf(resenaId));
        assertThat(evento.get("usuario")).isEqualTo(String.valueOf(administradorId));
        assertThat(evento.get("fecha")).isNotNull();
        assertThat((String) evento.get("datos")).contains(MOTIVO);
    }

    @Test
    @DisplayName("El huésped autor recibe una notificación con el motivo")
    void notificaAlAutor() throws Exception {
        resenaDe("ana@correo.com", 1, "Ofensiva");
        long resenaId = idDeResena("ana@correo.com");

        ocultar(iniciarSesion("admin@correo.com"), resenaId, MOTIVO).andExpect(status().isOk());

        Map<String, Object> notificacion = jdbc.queryForMap(
                "SELECT n.mensaje, n.tipo FROM notificacion n JOIN usuario u ON u.id = n.destinatario_id "
                        + "WHERE u.correo = 'ana@correo.com' AND n.tipo = 'RESENA_OCULTA'");
        assertThat((String) notificacion.get("mensaje")).contains(MOTIVO);
        mvc.perform(get("/api/v1/notificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC4: quien no es administrador recibe 403 y la reseña no cambia")
    void soloElAdministrador() throws Exception {
        resenaDe("ana@correo.com", 1, "Ofensiva");
        long resenaId = idDeResena("ana@correo.com");

        ocultar(iniciarSesion("ana@correo.com"), resenaId, MOTIVO).andExpect(status().isForbidden());
        ocultar(iniciarSesion("luis@correo.com"), resenaId, MOTIVO).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT estado FROM resena WHERE id = ?", String.class, resenaId))
                .isEqualTo("VISIBLE");
    }

    @Test
    @DisplayName("Sin token responde 401, sin motivo 400, reseña inexistente 404 y ya oculta 409")
    void casosBorde() throws Exception {
        resenaDe("ana@correo.com", 1, "Ofensiva");
        long resenaId = idDeResena("ana@correo.com");
        String admin = iniciarSesion("admin@correo.com");

        mvc.perform(post("/api/v1/admin/resenas/{id}/ocultar", resenaId)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(MOTIVO)))
                .andExpect(status().isUnauthorized());
        ocultar(admin, resenaId, "  ").andExpect(status().isBadRequest());
        ocultar(admin, resenaId + 1000, MOTIVO).andExpect(status().isNotFound());
        ocultar(admin, resenaId, MOTIVO).andExpect(status().isOk());
        ocultar(admin, resenaId, "Otro motivo").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT motivo_ocultacion FROM resena WHERE id = ?", String.class,
                resenaId)).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("El administrador lista las reseñas visibles, la más reciente primero y sin las ocultas")
    void listaLasVisibles() throws Exception {
        resenaDe("ana@correo.com", 4, "Muy cómodo");
        resenaDe("carla@correo.com", 1, "Ofensiva");
        long resenaAna = idDeResena("ana@correo.com");
        long resenaCarla = idDeResena("carla@correo.com");
        jdbc.update("UPDATE resena SET fecha = '2026-01-10T10:00:00Z' WHERE id = ?", resenaAna);
        jdbc.update("UPDATE resena SET fecha = '2026-02-10T10:00:00Z' WHERE id = ?", resenaCarla);
        String admin = iniciarSesion("admin@correo.com");

        mvc.perform(get("/api/v1/admin/resenas").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(resenaCarla))
                .andExpect(jsonPath("$[0].autor").value("Carla Ruiz"))
                .andExpect(jsonPath("$[0].publicacionId").value(publicacionId))
                .andExpect(jsonPath("$[1].id").value(resenaAna));

        ocultar(admin, resenaCarla, MOTIVO).andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/resenas").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(resenaAna));
    }

    @Test
    @DisplayName("El listado de moderación responde 403 a un huésped y 401 sin token")
    void listadoSoloAdministrador() throws Exception {
        mvc.perform(get("/api/v1/admin/resenas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/resenas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isForbidden());
    }

    private ResultActions ocultar(String token, long resenaId, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/admin/resenas/{id}/ocultar", resenaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(motivo)));
    }

    private String cuerpo(String motivo) {
        return """
                {"motivo": "%s"}
                """.formatted(motivo);
    }

    private long idDeResena(String correo) {
        return jdbc.queryForObject("SELECT r.id FROM resena r JOIN usuario u ON u.id = r.huesped_id "
                + "WHERE u.correo = ?", Long.class, correo);
    }

    // Crea la reserva COMPLETADA del huésped y su reseña por la API.
    private long resenaDe(String correo, int calificacion, String contenido) throws Exception {
        String token = iniciarSesion(correo);
        long reservaId = reservaCompletada(token);
        mvc.perform(post("/api/v1/reservas/{id}/resena", reservaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"calificacion": %d, "contenido": "%s"}
                                """.formatted(calificacion, contenido)))
                .andExpect(status().isCreated());
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

    // Igual que ConsultaResenasIntegracionTest: ACTIVA con precio fijo y un periodo que cubre las fechas.
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
