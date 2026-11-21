package co.edu.unbosque.marketplace.notificaciones.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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

/** Consulta y lectura de notificaciones (SOF-37): token → autorización → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificacionIntegracionTest {

    private static final String RUTA = "/api/v1/notificaciones";
    private static final String CONTRASENA = "Segura#2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    private Long luis;
    private Long ana;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM notificacion");
        usuarios.deleteAll();
        luis = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION)).getId();
        ana = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA),
                Rol.HUESPED)).getId();
        usuarios.save(Usuario.nuevo("Rosa Díaz", "rosa@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));
    }

    // Las notificaciones referencian al usuario: no se dejan filas para los tests de otros módulos.
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion");
    }

    @Test
    @DisplayName("AC5: cada usuario ve solo sus notificaciones, de la más nueva a la más vieja, con el contador de no leídas")
    void cadaUsuarioVeSoloLasSuyas() throws Exception {
        Long vieja = insertar(luis, "Se confirmó una reserva en «Casa» del 10/12/2026 al 14/12/2026.",
                Instant.parse("2026-11-20T10:00:00Z"));
        Long nueva = insertar(luis, "Se canceló una reserva en «Casa» del 10/12/2026 al 14/12/2026.",
                Instant.parse("2026-11-21T10:00:00Z"));
        insertar(ana, "Tu reserva en «Casa» del 10/12/2026 al 14/12/2026 quedó confirmada.",
                Instant.parse("2026-11-21T11:00:00Z"));

        listar(iniciarSesion("luis@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(2))
                .andExpect(jsonPath("$.notificaciones.length()").value(2))
                .andExpect(jsonPath("$.notificaciones[0].id").value(nueva.intValue()))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value("RESERVA_CONFIRMADA"))
                .andExpect(jsonPath("$.notificaciones[0].mensaje")
                        .value("Se canceló una reserva en «Casa» del 10/12/2026 al 14/12/2026."))
                .andExpect(jsonPath("$.notificaciones[0].entidad").value("Reserva"))
                .andExpect(jsonPath("$.notificaciones[0].entidadId").value(7))
                .andExpect(jsonPath("$.notificaciones[0].leida").value(false))
                .andExpect(jsonPath("$.notificaciones[0].creadaEn").value("2026-11-21T10:00:00Z"))
                .andExpect(jsonPath("$.notificaciones[1].id").value(vieja.intValue()));
    }

    @Test
    @DisplayName("AC4: abrir una notificación la marca leída y baja el contador; marcarla de nuevo no cambia nada")
    void leerBajaElContadorYEsIdempotente() throws Exception {
        Long id = insertar(luis, "Tu publicación «Casa» ahora está activa.", Instant.parse("2026-11-21T10:00:00Z"));
        insertar(luis, "Tu publicación «Casa» ahora está pausada.", Instant.parse("2026-11-21T11:00:00Z"));
        String token = iniciarSesion("luis@correo.com");

        leer(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.leida").value(true))
                .andExpect(jsonPath("$.mensaje").value("Tu publicación «Casa» ahora está activa."));
        Timestamp primeraLectura = leidaEn(id);
        assertThat(primeraLectura).isNotNull();
        listar(token).andExpect(jsonPath("$.noLeidas").value(1));

        leer(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true));
        assertThat(leidaEn(id)).isEqualTo(primeraLectura);
        listar(token).andExpect(jsonPath("$.noLeidas").value(1));
    }

    @Test
    @DisplayName("AC5: marcar la notificación de otro usuario, o una que no existe, responde 404 y no la toca")
    void leerAjenaResponde404() throws Exception {
        Long deAna = insertar(ana, "Tu reserva fue cancelada.", Instant.parse("2026-11-21T10:00:00Z"));
        String token = iniciarSesion("luis@correo.com");

        leer(token, deAna)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa notificación."));
        leer(token, deAna + 1000).andExpect(status().isNotFound());
        assertThat(leidaEn(deAna)).isNull();
    }

    @Test
    @DisplayName("Sin sesión, consultar o marcar notificaciones responde 401")
    void sinSesionResponde401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA + "/1/leer")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC3: al restringir una cuenta, la persona queda con la notificación y el motivo completo")
    void restringirDejaLaNotificacion() throws Exception {
        String motivo = "x".repeat(500);
        mvc.perform(post("/api/v1/admin/usuarios/" + ana + "/restringir")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("rosa@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"" + motivo + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("SELECT tipo, mensaje, entidad, entidad_id FROM notificacion "
                + "WHERE destinatario_id = ?", ana))
                .containsEntry("tipo", "CUENTA_RESTRINGIDA")
                .containsEntry("mensaje", "Un administrador restringió tu cuenta. Motivo: " + motivo)
                .containsEntry("entidad", "Usuario")
                .containsEntry("entidad_id", ana);
    }

    private Long insertar(Long destinatario, String mensaje, Instant creadoEn) {
        return jdbc.queryForObject("INSERT INTO notificacion (destinatario_id, tipo, mensaje, entidad, entidad_id, "
                + "creado_en) VALUES (?, 'RESERVA_CONFIRMADA', ?, 'Reserva', 7, ?) RETURNING id", Long.class,
                destinatario, mensaje, Timestamp.from(creadoEn));
    }

    private Timestamp leidaEn(Long id) {
        return jdbc.queryForObject("SELECT leida_en FROM notificacion WHERE id = ?", Timestamp.class, id);
    }

    private ResultActions listar(String token) throws Exception {
        return mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions leer(String token, Long id) throws Exception {
        return mvc.perform(post(RUTA + "/" + id + "/leer").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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
