package co.edu.unbosque.marketplace.resenas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Creación de la reseña tras la estancia (SOF-80, HU-35): token → autorización → service → PostgreSQL. El
 * flujo completo de cada criterio de aceptación contra la base real, igual que CancelacionReservaIntegracionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResenaIntegracionTest {

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

    // Las reseñas y las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
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
    @DisplayName("AC1: con la reserva COMPLETADA la reseña queda con huésped, reserva, alojamiento, fecha y visible")
    void laResenaQuedaAsociada() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long reservaId = reservaCompletada(token);
        Long huespedId = jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", Long.class, "ana@correo.com");

        resenar(token, reservaId, 4, "Muy cómodo y limpio")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(reservaId))
                .andExpect(jsonPath("$.publicacionId").value(publicacionId))
                .andExpect(jsonPath("$.calificacion").value(4))
                .andExpect(jsonPath("$.contenido").value("Muy cómodo y limpio"));

        Map<String, Object> fila = filaResena(reservaId);
        assertThat(fila).containsEntry("huesped_id", huespedId)
                .containsEntry("publicacion_id", publicacionId)
                .containsEntry("calificacion", 4)
                .containsEntry("contenido", "Muy cómodo y limpio")
                .containsEntry("estado", "VISIBLE");
        assertThat(fila.get("fecha")).isNotNull();
    }

    @Test
    @DisplayName("AC2: con la reserva CONFIRMADA y la estancia sin ocurrir el sistema impide reseñar")
    void unaReservaConfirmadaNoSeResena() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long reservaId = reservarYPagar(token);

        resenar(token, reservaId, 5, "Todavía no fui")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Todavía no puedes reseñar: la estancia aún no ha terminado."));

        assertThat(cuentaResenas(reservaId)).isZero();
    }

    @Test
    @DisplayName("AC3: reseñar por segunda vez la misma reserva se rechaza e indica que ya existe una reseña")
    void noSeResenaDosVeces() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long reservaId = reservaCompletada(token);
        resenar(token, reservaId, 5, "Primera").andExpect(status().isCreated());

        resenar(token, reservaId, 1, "Segunda")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una reseña para esta reserva."));

        assertThat(cuentaResenas(reservaId)).isEqualTo(1);
        assertThat(filaResena(reservaId)).containsEntry("contenido", "Primera");
    }

    @Test
    @DisplayName("AC4: reseñar la reserva de otro huésped responde 403 y no guarda nada")
    void unaReservaAjenaSeNiega() throws Exception {
        long reservaId = reservaCompletada(iniciarSesion("ana@correo.com"));

        resenar(iniciarSesion("carla@correo.com"), reservaId, 5, "No me hospedé aquí")
                .andExpect(status().isForbidden());

        assertThat(cuentaResenas(reservaId)).isZero();
    }

    @Test
    @DisplayName("Un anfitrión no reseña: el rol no es el de huésped y responde 403")
    void unAnfitrionNoResena() throws Exception {
        long reservaId = reservaCompletada(iniciarSesion("ana@correo.com"));

        resenar(iniciarSesion("luis@correo.com"), reservaId, 5, "Soy el anfitrión")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una reserva que no existe responde 404")
    void unaReservaInexistenteResponde404() throws Exception {
        resenar(iniciarSesion("ana@correo.com"), 999_999L, 5, "Comentario")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa reserva."));
    }

    @Test
    @DisplayName("La calificación de 1 a 5 y el comentario son obligatorios: si no, la API responde 400")
    void laCalificacionYElComentarioSonObligatorios() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long reservaId = reservaCompletada(token);

        resenar(token, reservaId, 6, "Fuera de rango")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.calificacion").value("La calificación va de 1 a 5"));
        resenar(token, reservaId, 0, "Fuera de rango")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reservas/{id}/resena", reservaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.calificacion").value("Indica la calificación"))
                .andExpect(jsonPath("$.errores.contenido").value("Escribe un comentario"));

        assertThat(cuentaResenas(reservaId)).isZero();
    }

    @Test
    @DisplayName("Sin token, reseñar responde 401")
    void sinTokenResponde401() throws Exception {
        long reservaId = reservaCompletada(iniciarSesion("ana@correo.com"));

        mvc.perform(post("/api/v1/reservas/{id}/resena", reservaId).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoResena(5, "Sin sesión")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La reseña creada queda en la auditoría con la acción RESENA")
    void laResenaQuedaAuditada() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long reservaId = reservaCompletada(token);

        resenar(token, reservaId, 5, "Excelente").andExpect(status().isCreated());

        Integer eventos = jdbc.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'RESENA' AND entidad_id = ? "
                        + "AND resultado = 'EXITO'", Integer.class, String.valueOf(reservaId));
        assertThat(eventos).isEqualTo(1);
    }

    private ResultActions resenar(String token, long reservaId, int calificacion, String contenido)
            throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/resena", reservaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoResena(calificacion, contenido)));
    }

    private static String cuerpoResena(int calificacion, String contenido) {
        return """
                {"calificacion": %d, "contenido": "%s"}
                """.formatted(calificacion, contenido);
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

    private Map<String, Object> filaResena(long reservaId) {
        return jdbc.queryForMap("SELECT huesped_id, publicacion_id, calificacion, contenido, estado, fecha "
                + "FROM resena WHERE reserva_id = ?", reservaId);
    }

    private int cuentaResenas(long reservaId) {
        return jdbc.queryForObject("SELECT count(*) FROM resena WHERE reserva_id = ?", Integer.class, reservaId);
    }

    // Igual que CancelacionReservaIntegracionTest: ACTIVA con precio fijo y un periodo que cubre las fechas.
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
