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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
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
 * Revisión manual de las reservas de una publicación bloqueada (SOF-83, AC3, S7): token → autorización →
 * service → pasarela simulada (pagos.modo por defecto) → PostgreSQL. La reserva llega a revisión por el
 * bloqueo real de la publicación.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RevisionManualReservasIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String MOTIVO_BLOQUEO = "Las fotos no corresponden al alojamiento";
    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LLEGADA.plusDays(4);

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

    private Long anfitrionId;
    private Long huespedId;
    private Long administradorId;
    private Long publicacionId;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        usuarios.deleteAll();
        anfitrionId = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION)).getId();
        huespedId = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA),
                Rol.HUESPED)).getId();
        administradorId = usuarios.save(Usuario.nuevo("Rosa Díaz", "rosa@correo.com",
                codificador.encode(CONTRASENA), Rol.ADMINISTRADOR)).getId();
        publicacionId = publicacionActiva();
    }

    // Orden de llaves foráneas: notificaciones y cancelaciones antes que reservas, reservas antes que publicaciones.
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion");
        jdbc.update("DELETE FROM cancelacion");
        jdbc.update("DELETE FROM reserva");
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("GET /revision lista la reserva de la publicación bloqueada con su título y el motivo")
    void listaLasReservasEnRevision() throws Exception {
        Long id = reservaEnRevision("sim_" + UUID.randomUUID());

        mvc.perform(get("/api/v1/admin/reservas/revision").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.intValue()))
                .andExpect(jsonPath("$[0].publicacionId").value(publicacionId.intValue()))
                .andExpect(jsonPath("$[0].titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$[0].huespedId").value(huespedId.intValue()))
                .andExpect(jsonPath("$[0].llegada").value(LLEGADA.toString()))
                .andExpect(jsonPath("$[0].estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$[0].motivoBloqueo").value(MOTIVO_BLOQUEO));
    }

    @Test
    @DisplayName("AC3: CONFIRMAR deja la reserva CONFIRMADA y avisa al huésped y al anfitrión")
    void confirmarLaDejaConfirmada() throws Exception {
        Long id = reservaEnRevision("sim_" + UUID.randomUUID());

        resolver(admin(), id, "CONFIRMAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.reembolsado").value(false));

        assertThat(jdbc.queryForMap("SELECT estado, estado_pago FROM reserva WHERE id = ?", id))
                .containsEntry("estado", "CONFIRMADA").containsEntry("estado_pago", "APROBADO");
        assertThat(avisos(id, "RESERVA_CONFIRMADA")).containsExactlyInAnyOrder(huespedId, anfitrionId);
    }

    @Test
    @DisplayName("AC3/S7: CANCELAR devuelve el cobro, deja la reserva CANCELADA con el pago REEMBOLSADO y la cancelación registrada")
    void cancelarReembolsaYRegistraLaCancelacion() throws Exception {
        Long id = reservaEnRevision("sim_" + UUID.randomUUID());

        resolver(admin(), id, "CANCELAR", "Publicación engañosa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.reembolsado").value(true))
                .andExpect(jsonPath("$.valorDevolucion").value(470000));

        assertThat(jdbc.queryForMap("SELECT estado, estado_pago FROM reserva WHERE id = ?", id))
                .containsEntry("estado", "CANCELADA").containsEntry("estado_pago", "REEMBOLSADO");
        Map<String, Object> cancelacion = jdbc.queryForMap(
                "SELECT usuario_id, motivo, valor_devolucion, estado_final FROM cancelacion WHERE reserva_id = ?", id);
        assertThat(cancelacion).containsEntry("usuario_id", administradorId)
                .containsEntry("motivo", "Publicación engañosa").containsEntry("estado_final", "CANCELADA");
        assertThat(((Number) cancelacion.get("valor_devolucion")).intValue()).isEqualTo(470000);
        assertThat(avisos(id, "RESERVA_CANCELADA")).containsExactlyInAnyOrder(huespedId, anfitrionId);
    }

    @Test
    @DisplayName("CANCELAR sin motivo responde 400 con el error del campo y la reserva sigue en revisión")
    void cancelarSinMotivoResponde400() throws Exception {
        Long id = reservaEnRevision("sim_" + UUID.randomUUID());

        resolver(admin(), id, "CANCELAR", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("El motivo es obligatorio para cancelar."));
        resolver(admin(), id, "APLAZAR", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.decision").value("La decisión debe ser CONFIRMAR o CANCELAR."));

        assertThat(estado(id)).isEqualTo("EN_REVISION_MANUAL");
    }

    @Test
    @DisplayName("Resolver una reserva que no está en revisión responde 409; una inexistente, 404")
    void resolverFueraDeRevision() throws Exception {
        Long id = reservaEnRevision("sim_" + UUID.randomUUID());
        resolver(admin(), id, "CONFIRMAR", null).andExpect(status().isOk());

        resolver(admin(), id, "CANCELAR", "Otra vez")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se puede resolver una reserva en revisión manual."));
        resolver(admin(), id + 1000, "CONFIRMAR", null).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancelacion", Long.class)).isZero();
    }

    @Test
    @DisplayName("Solo un administrador ve y resuelve la revisión: el anfitrión y el huésped reciben 403")
    void soloElAdministrador() throws Exception {
        Long id = reservaEnRevision("sim_" + UUID.randomUUID());
        for (String correo : new String[] {"luis@correo.com", "ana@correo.com"}) {
            String token = iniciarSesion(correo);
            mvc.perform(get("/api/v1/admin/reservas/revision").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
            resolver(token, id, "CONFIRMAR", null).andExpect(status().isForbidden());
        }
        assertThat(estado(id)).isEqualTo("EN_REVISION_MANUAL");
    }

    // Siembra una CONFIRMADA pagada y bloquea la publicación por el endpoint real: así llega a revisión.
    private Long reservaEnRevision(String transaccionId) throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en,
                    estado_pago, transaccion_id)
                VALUES (?, ?, ?, ?, 'CONFIRMADA', 4, 400000, 30000, 40000, 0, 0, 470000, now(), 'APROBADO', ?)
                RETURNING id
                """, Long.class, publicacionId, huespedId, LLEGADA, SALIDA, transaccionId);
        mvc.perform(post("/api/v1/admin/publicaciones/{id}/bloquear", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", MOTIVO_BLOQUEO))))
                .andExpect(status().isOk());
        assertThat(estado(id)).isEqualTo("EN_REVISION_MANUAL");
        return id;
    }

    private ResultActions resolver(String token, Long id, String decision, String motivo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("decision", decision);
        cuerpo.put("motivo", motivo);
        return mvc.perform(post("/api/v1/admin/reservas/{id}/resolver", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private java.util.List<Long> avisos(Long reservaId, String tipo) {
        return jdbc.queryForList("SELECT destinatario_id FROM notificacion WHERE entidad = 'Reserva' "
                + "AND entidad_id = ? AND tipo = ?", Long.class, reservaId, tipo);
    }

    private String estado(Long id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    private String admin() throws Exception {
        return iniciarSesion("rosa@correo.com");
    }

    private Long publicacionActiva() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("titulo", "Casa en la playa"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = 'ACTIVA', precio_noche = 100000 WHERE id = ?", id);
        return id;
    }

    private String iniciarSesion(String correo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", correo, "contrasena", CONTRASENA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
