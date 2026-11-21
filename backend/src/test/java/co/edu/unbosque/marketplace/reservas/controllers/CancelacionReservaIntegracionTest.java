package co.edu.unbosque.marketplace.reservas.controllers;

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
 * Cancelación de una reserva CONFIRMADA (SOF-75, HU-30): token → autorización → service → PostgreSQL. El
 * flujo completo de cada criterio de aceptación contra la base real, igual que PagoReservaIntegracionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CancelacionReservaIntegracionTest {

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
        jdbc.update("DELETE FROM cancelacion");
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Carla Ruiz", "carla@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        publicacionId = publicacionActiva();
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM cancelacion");
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("AC1: el huésped titular cancela su reserva CONFIRMADA con un motivo y el periodo queda libre")
    void elHuespedCancelaYLiberaElPeriodo() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservarYPagar(token);

        cancelar(token, id, "Cambio de planes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(id))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivo").value("Cambio de planes"))
                .andExpect(jsonPath("$.valorDevolucion").isNumber());

        assertThat(fila(id)).containsEntry("estado", "CANCELADA");
        assertThat(filaCancelacion(id)).containsEntry("motivo", "Cambio de planes")
                .containsEntry("estado_final", "CANCELADA");
        // El periodo queda libre: otro huésped puede reservar las mismas fechas.
        reservar(iniciarSesion("carla@correo.com"));
    }

    @Test
    @DisplayName("AC2: la cancelación registra usuario, fecha, motivo, devolución y el estado final")
    void laCancelacionRegistraElDesglose() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservarYPagar(token);
        Long huespedId = jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", Long.class, "ana@correo.com");
        Map<String, Object> reserva = fila(id);

        cancelar(token, id, "Cambio de planes").andExpect(status().isOk());

        Map<String, Object> cancelacion = filaCancelacion(id);
        assertThat(cancelacion).containsEntry("usuario_id", huespedId).containsEntry("motivo", "Cambio de planes")
                .containsEntry("estado_final", "CANCELADA");
        assertThat(cancelacion.get("fecha")).isNotNull();
        assertThat(((Number) cancelacion.get("valor_devolucion")).doubleValue())
                .isEqualTo(((Number) reserva.get("total")).doubleValue());
    }

    @Test
    @DisplayName("AC3: el anfitrión también puede cancelar y la devolución es del 100%")
    void elAnfitrionCancelaConDevolucionCompleta() throws Exception {
        long id = reservarYPagar(iniciarSesion("ana@correo.com"));
        Map<String, Object> reserva = fila(id);
        String tokenAnfitrion = iniciarSesion("luis@correo.com");

        cancelar(tokenAnfitrion, id, "El alojamiento no estará disponible")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        Map<String, Object> cancelacion = filaCancelacion(id);
        assertThat(((Number) cancelacion.get("valor_devolucion")).doubleValue())
                .isEqualTo(((Number) reserva.get("total")).doubleValue());
    }

    @Test
    @DisplayName("AC4: si la estancia ya terminó, la cancelación se rechaza e informa el motivo")
    void unaEstanciaYaTerminadaNoSePuedeCancelar() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservarYPagar(token);
        jdbc.update("UPDATE reserva SET llegada = ?, salida = ? WHERE id = ?",
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(6), id);

        cancelar(token, id, "Cambio de planes")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La estancia ya terminó: no se puede cancelar."));

        assertThat(fila(id)).containsEntry("estado", "CONFIRMADA");
    }

    @Test
    @DisplayName("Una reserva PENDIENTE (todavía sin pago) no se puede cancelar")
    void unaReservaPendienteNoSePuedeCancelar() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservar(token);

        cancelar(token, id, "Cambio de planes")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta reserva todavía no está confirmada: no se puede cancelar."));
    }

    @Test
    @DisplayName("AC5: quien no es ni el huésped ni el anfitrión recibe 403 y no cambia la reserva")
    void unUsuarioSinNingunaPropiedadRecibe403() throws Exception {
        long id = reservarYPagar(iniciarSesion("ana@correo.com"));

        cancelar(iniciarSesion("carla@correo.com"), id, "Motivo").andExpect(status().isForbidden());

        assertThat(fila(id)).containsEntry("estado", "CONFIRMADA");
    }

    @Test
    @DisplayName("Una reserva que no existe responde 404")
    void unaReservaInexistenteResponde404() throws Exception {
        cancelar(iniciarSesion("ana@correo.com"), 999_999L, "Motivo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa reserva."));
    }

    @Test
    @DisplayName("El motivo es obligatorio: sin él la API responde 400")
    void elMotivoEsObligatorio() throws Exception {
        String token = iniciarSesion("ana@correo.com");
        long id = reservarYPagar(token);

        mvc.perform(post("/api/v1/reservas/{id}/cancelacion", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("Indica el motivo de la cancelación"));
    }

    @Test
    @DisplayName("Sin token, cancelar responde 401")
    void sinTokenResponde401() throws Exception {
        long id = reservarYPagar(iniciarSesion("ana@correo.com"));

        mvc.perform(post("/api/v1/reservas/{id}/cancelacion", id).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCancelacion("Motivo")))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions cancelar(String token, long id, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/cancelacion", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion(motivo)));
    }

    private static String cuerpoCancelacion(String motivo) {
        return """
                {"motivo": "%s"}
                """.formatted(motivo);
    }

    private long reservarYPagar(String token) throws Exception {
        long id = reservar(token);
        mvc.perform(post("/api/v1/reservas/{id}/pago", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"metodoPago": "pm_card_visa"}
                                """))
                .andExpect(status().isOk());
        return id;
    }

    private long reservar(String token) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private String cuerpoReserva() {
        return """
                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": []}
                """.formatted(publicacionId, LLEGADA, SALIDA);
    }

    private Map<String, Object> fila(long id) {
        return jdbc.queryForMap("SELECT estado, total FROM reserva WHERE id = ?", id);
    }

    private Map<String, Object> filaCancelacion(long id) {
        return jdbc.queryForMap("SELECT usuario_id, motivo, valor_devolucion, estado_final, fecha "
                + "FROM cancelacion WHERE reserva_id = ?", id);
    }

    // Igual que PagoReservaIntegracionTest: ACTIVA con precio fijo y un periodo que cubre las fechas de las pruebas.
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
