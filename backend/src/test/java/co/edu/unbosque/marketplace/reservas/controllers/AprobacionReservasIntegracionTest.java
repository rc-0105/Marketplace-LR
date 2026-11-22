package co.edu.unbosque.marketplace.reservas.controllers;

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
import co.edu.unbosque.marketplace.reservas.services.AprobacionReservasService;
import co.edu.unbosque.marketplace.reservas.services.RegistroReservas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Aprobación de solicitudes de reserva por el anfitrión (SOF-79, HU-34, RF-52): token, autorización, service y
 * PostgreSQL de punta a punta, incluidas las carreras entre aprobar, rechazar y vencer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AprobacionReservasIntegracionTest {

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
    @Autowired
    private AprobacionReservasService aprobacion;
    @Autowired
    private RegistroReservas registro;

    private Long publicacionId;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Marta Rey", "marta@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Carla Ruiz", "carla@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        publicacionId = publicacionPorAprobacion();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion");
        jdbc.update("DELETE FROM cancelacion");
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
    }

    // ---- listado ----

    @Test
    @DisplayName("El anfitrión lista solo las solicitudes vigentes de sus publicaciones; otro anfitrión no ve ninguna")
    void listaLasSolicitudesDelAnfitrion() throws Exception {
        long id = solicitar("ana@correo.com");

        mvc.perform(get("/api/v1/reservas/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer("luis@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].publicacionId").value(publicacionId))
                .andExpect(jsonPath("$[0].titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$[0].huespedNombre").value("Ana Gómez"))
                .andExpect(jsonPath("$[0].llegada").value(LLEGADA.toString()))
                .andExpect(jsonPath("$[0].salida").value(SALIDA.toString()))
                .andExpect(jsonPath("$[0].noches").value(4))
                .andExpect(jsonPath("$[0].total").isNumber())
                .andExpect(jsonPath("$[0].venceEn").isString())
                .andExpect(jsonPath("$[0].huespedCorreo").doesNotExist());

        mvc.perform(get("/api/v1/reservas/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer("marta@correo.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("El listado omite las solicitudes ya vencidas, aunque la tarea aún no las haya pasado a VENCIDA")
    void elListadoOmiteLasVencidas() throws Exception {
        long id = solicitar("ana@correo.com");
        vencerAhora(id);

        mvc.perform(get("/api/v1/reservas/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer("luis@correo.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Un huésped no puede listar, aprobar ni rechazar: 403")
    void unHuespedRecibe403() throws Exception {
        long id = solicitar("ana@correo.com");
        String token = bearer("ana@correo.com");

        mvc.perform(get("/api/v1/reservas/solicitudes").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        aprobar(token, id).andExpect(status().isForbidden());
        rechazar(token, id, "No").andExpect(status().isForbidden());
        assertThat(estado(id)).isEqualTo("PENDIENTE_APROBACION");
    }

    // ---- aprobar ----

    @Test
    @DisplayName("Aprobar: PENDIENTE con apartado nuevo de 24 h (venceEn escrito por la consulta masiva) y aviso al huésped")
    void apruebaLaSolicitud() throws Exception {
        long id = solicitar("ana@correo.com");
        Instant antes = Instant.now();

        aprobar(bearer("luis@correo.com"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(id))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.venceEn").isString())
                .andExpect(jsonPath("$.motivo").doesNotExist());

        assertThat(estado(id)).isEqualTo("PENDIENTE");
        Instant venceEn = venceEn(id);
        assertThat(venceEn).isBetween(antes.plus(Duration.ofHours(24)).minusSeconds(5),
                Instant.now().plus(Duration.ofHours(24)).plusSeconds(5));
        assertThat(notificaciones("ana@correo.com", "SOLICITUD_APROBADA")).hasSize(1);
        assertThat(notificaciones("ana@correo.com", "SOLICITUD_APROBADA").get(0)).asString().contains("Casa en la playa");
    }

    @Test
    @DisplayName("Aprobar y luego pagar por el flujo idempotente de siempre deja la reserva CONFIRMADA")
    void aprobarPagarConfirma() throws Exception {
        long id = solicitar("ana@correo.com");
        aprobar(bearer("luis@correo.com"), id).andExpect(status().isOk());

        mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, bearer("ana@correo.com"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"metodoPago": "pm_card_visa"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        assertThat(estado(id)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("Aprobar: el anfitrión de otra publicación recibe 403; una reserva inexistente, 404")
    void aprobarDeOtroAnfitrionEs403() throws Exception {
        long id = solicitar("ana@correo.com");

        aprobar(bearer("marta@correo.com"), id).andExpect(status().isForbidden());
        aprobar(bearer("luis@correo.com"), 999_999L).andExpect(status().isNotFound());
        rechazar(bearer("marta@correo.com"), id, "No").andExpect(status().isForbidden());
        assertThat(estado(id)).isEqualTo("PENDIENTE_APROBACION");
    }

    @Test
    @DisplayName("Aprobar dos veces, o aprobar una ya rechazada: 409")
    void aprobarDosVecesEs409() throws Exception {
        long id = solicitar("ana@correo.com");
        String token = bearer("luis@correo.com");

        aprobar(token, id).andExpect(status().isOk());
        aprobar(token, id).andExpect(status().isConflict());
        rechazar(token, id, "Tarde").andExpect(status().isConflict());
        assertThat(estado(id)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("Aprobar una solicitud cuya ventana ya se agotó (la tarea aún no la venció): 409 y sigue sin cambiar")
    void aprobarVencidaEs409() throws Exception {
        long id = solicitar("ana@correo.com");
        vencerAhora(id);

        aprobar(bearer("luis@correo.com"), id).andExpect(status().isConflict());

        assertThat(estado(id)).isEqualTo("PENDIENTE_APROBACION");
        assertThat(notificaciones("ana@correo.com", "SOLICITUD_APROBADA")).isEmpty();
    }

    @Test
    @DisplayName("Aprobar con la publicación bloqueada por un administrador (SOF-83): 409")
    void aprobarConPublicacionBloqueadaEs409() throws Exception {
        long id = solicitar("ana@correo.com");
        jdbc.update("UPDATE publicacion SET estado = 'BLOQUEADA', motivo_bloqueo = 'Fraude' WHERE id = ?",
                publicacionId);

        aprobar(bearer("luis@correo.com"), id).andExpect(status().isConflict());

        assertThat(estado(id)).isEqualTo("PENDIENTE_APROBACION");
    }

    // ---- rechazar ----

    @Test
    @DisplayName("Rechazar: RECHAZADA con motivo guardado, noches liberadas y aviso al huésped con el motivo")
    void rechazaLaSolicitud() throws Exception {
        long id = solicitar("ana@correo.com");

        rechazar(bearer("luis@correo.com"), id, "  Fechas no disponibles  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(id))
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivo").value("Fechas no disponibles"))
                .andExpect(jsonPath("$.venceEn").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT motivo_rechazo FROM reserva WHERE id = ?", String.class, id))
                .isEqualTo("Fechas no disponibles");
        assertThat(notificaciones("ana@correo.com", "SOLICITUD_RECHAZADA").get(0)).asString()
                .contains("Fechas no disponibles");
        // Las noches quedan libres: otro huésped puede solicitar las mismas fechas.
        solicitar("carla@correo.com");
    }

    @Test
    @DisplayName("El detalle de la reserva del huésped trae el motivo del rechazo; sin rechazo viene nulo")
    void elDetalleTraeElMotivoDelRechazo() throws Exception {
        long id = solicitar("ana@correo.com");
        mvc.perform(get("/api/v1/reservas/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer("ana@correo.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.motivoRechazo").value((Object) null));

        rechazar(bearer("luis@correo.com"), id, "Fechas no disponibles").andExpect(status().isOk());

        mvc.perform(get("/api/v1/reservas/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Fechas no disponibles"));
    }

    @Test
    @DisplayName("Rechazar con el motivo de 500 caracteres: se guarda completo y el aviso cabe en la notificación")
    void rechazarConMotivoMaximoCabeEnLaNotificacion() throws Exception {
        long id = solicitar("ana@correo.com");
        String motivo = "x".repeat(500);

        rechazar(bearer("luis@correo.com"), id, motivo).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT motivo_rechazo FROM reserva WHERE id = ?", String.class, id))
                .hasSize(500);
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT mensaje FROM notificacion WHERE tipo = 'SOLICITUD_RECHAZADA'");
        assertThat(filas).hasSize(1);
        assertThat(((String) filas.get(0).get("mensaje")).length()).isLessThanOrEqualTo(600);
    }

    @Test
    @DisplayName("Rechazar: motivo vacío, ausente o de más de 500 caracteres responde 400 y no cambia nada")
    void rechazarConMotivoInvalidoEs400() throws Exception {
        long id = solicitar("ana@correo.com");
        String token = bearer("luis@correo.com");

        mvc.perform(post("/api/v1/reservas/{id}/rechazar", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        rechazar(token, id, "   ").andExpect(status().isBadRequest());
        rechazar(token, id, "x".repeat(501)).andExpect(status().isBadRequest());

        assertThat(estado(id)).isEqualTo("PENDIENTE_APROBACION");
    }

    @Test
    @DisplayName("Una solicitud RECHAZADA no se puede pagar: 409")
    void unaRechazadaNoSePaga() throws Exception {
        long id = solicitar("ana@correo.com");
        rechazar(bearer("luis@correo.com"), id, "No").andExpect(status().isOk());

        mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, bearer("ana@correo.com"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"metodoPago": "pm_card_visa"}
                                """))
                .andExpect(status().isConflict());
    }

    // ---- ayudas ----

    private ResultActions aprobar(String token, long id) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/aprobar", id).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions rechazar(String token, long id, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/rechazar", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("motivo", motivo))));
    }

    private String estado(long id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    private Instant venceEn(long id) {
        return jdbc.queryForObject("SELECT vence_en FROM reserva WHERE id = ?", Timestamp.class, id).toInstant();
    }

    // Agota la ventana del anfitrión sin pasar por el reloj: la tarea programada aún no la ha vencido.
    private void vencerAhora(long id) {
        jdbc.update("UPDATE reserva SET vence_en = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(60)), id);
    }

    private List<String> notificaciones(String correo, String tipo) {
        return jdbc.queryForList("SELECT n.mensaje FROM notificacion n JOIN usuario u ON u.id = n.destinatario_id"
                + " WHERE u.correo = ? AND n.tipo = ?", String.class, correo, tipo);
    }

    private long solicitar(String correo) throws Exception {
        return solicitar(correo, LLEGADA, SALIDA);
    }

    private long solicitar(String correo, LocalDate llegada, LocalDate salida) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, bearer(correo))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": []}
                                """.formatted(publicacionId, llegada, salida)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private Long publicacionPorAprobacion() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = 'ACTIVA', precio_noche = 100000, tarifa_limpieza = 30000, "
                + "modo_reserva = 'APROBACION' WHERE id = ?", id);
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)",
                id, LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
        return id;
    }

    private String bearer(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(respuesta).get("token").asText();
    }
}
