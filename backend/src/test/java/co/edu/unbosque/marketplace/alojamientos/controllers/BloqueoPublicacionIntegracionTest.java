package co.edu.unbosque.marketplace.alojamientos.controllers;

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
 * Bloqueo administrativo de una publicación engañosa (SOF-83, HU-38): token → autorización → service →
 * PostgreSQL, con la migración V29 aplicada. Las reservas se siembran con JdbcTemplate en el estado que cada
 * caso necesita.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BloqueoPublicacionIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String MOTIVO = "Las fotos no corresponden al alojamiento";
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
    private Long publicacionId;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        usuarios.deleteAll();
        anfitrionId = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION)).getId();
        huespedId = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA),
                Rol.HUESPED)).getId();
        usuarios.save(Usuario.nuevo("Rosa Díaz", "rosa@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));
        publicacionId = publicacionActiva("Casa en la playa");
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus publicaciones ni usuarios.
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion");
        jdbc.update("DELETE FROM cancelacion");
        jdbc.update("DELETE FROM reserva");
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("AC1: bloquear con motivo la deja BLOQUEADA, guarda el motivo y avisa al anfitrión")
    void bloquearConMotivo() throws Exception {
        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicacionId.intValue()))
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.motivoBloqueo").value(MOTIVO))
                .andExpect(jsonPath("$.bloqueadaEn").isNotEmpty());

        assertThat(jdbc.queryForMap("SELECT estado, motivo_bloqueo FROM publicacion WHERE id = ?", publicacionId))
                .containsEntry("estado", "BLOQUEADA").containsEntry("motivo_bloqueo", MOTIVO);
        assertThat(jdbc.queryForList("SELECT mensaje FROM notificacion WHERE destinatario_id = ? AND tipo = ?",
                String.class, anfitrionId, "PUBLICACION_ESTADO"))
                .containsExactly("Tu publicación «Casa en la playa» ahora está bloqueada. Motivo: " + MOTIVO);
    }

    @Test
    @DisplayName("AC1: la publicación bloqueada sale de la búsqueda y del detalle, y no admite reservas nuevas")
    void laBloqueadaNoSeVeNiSeReserva() throws Exception {
        String huesped = iniciarSesion("ana@correo.com");
        buscar(huesped).andExpect(jsonPath("$.length()").value(1));

        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, MOTIVO).andExpect(status().isOk());

        buscar(huesped).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/busqueda/{id}", publicacionId).header(HttpHeaders.AUTHORIZATION, "Bearer " + huesped))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + huesped)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": []}
                                """.formatted(publicacionId, LLEGADA, SALIDA)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva", Long.class)).isZero();
    }

    @Test
    @DisplayName("AC2: las reservas confirmadas futuras pasan a revisión manual; las terminadas y las de otra publicación no")
    void lasReservasFuturasPasanARevision() throws Exception {
        Long futura = sembrarReserva(publicacionId, "CONFIRMADA", LLEGADA, SALIDA);
        Long terminada = sembrarReserva(publicacionId, "CONFIRMADA", LocalDate.now().minusDays(6),
                LocalDate.now().minusDays(2));
        Long cancelada = sembrarReserva(publicacionId, "CANCELADA", LLEGADA.plusDays(10), SALIDA.plusDays(10));
        Long otraPublicacion = sembrarReserva(publicacionActiva("Cabaña en el bosque"), "CONFIRMADA", LLEGADA, SALIDA);

        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, MOTIVO).andExpect(status().isOk());

        assertThat(estadoReserva(futura)).isEqualTo("EN_REVISION_MANUAL");
        assertThat(estadoReserva(terminada)).isEqualTo("CONFIRMADA");
        assertThat(estadoReserva(cancelada)).isEqualTo("CANCELADA");
        assertThat(estadoReserva(otraPublicacion)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("AC2: una reserva en revisión manual sigue ocupando sus noches: la base rechaza otra que se cruce")
    void laReservaEnRevisionSigueOcupando() throws Exception {
        sembrarReserva(publicacionId, "CONFIRMADA", LLEGADA, SALIDA);
        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, MOTIVO).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> sembrarReserva(publicacionId, "CONFIRMADA", LLEGADA.plusDays(1), SALIDA.plusDays(1)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Bloquear una publicación que no está activa ni pausada responde 409 y no la cambia")
    void bloquearEnOtroEstadoResponde409() throws Exception {
        jdbc.update("UPDATE publicacion SET estado = 'BORRADOR' WHERE id = ?", publicacionId);

        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, MOTIVO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se puede bloquear una publicación activa o pausada."));

        assertThat(jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, publicacionId))
                .isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Bloquear sin motivo responde 400 con el error del campo y no la cambia")
    void bloquearSinMotivoResponde400() throws Exception {
        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("El motivo es obligatorio."));

        assertThat(jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, publicacionId))
                .isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Bloquear una publicación que no existe responde 404")
    void bloquearInexistenteResponde404() throws Exception {
        bloquear(iniciarSesion("rosa@correo.com"), publicacionId + 1000, MOTIVO).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Solo un administrador bloquea: el anfitrión y el huésped reciben 403")
    void soloElAdministradorBloquea() throws Exception {
        bloquear(iniciarSesion("luis@correo.com"), publicacionId, MOTIVO).andExpect(status().isForbidden());
        bloquear(iniciarSesion("ana@correo.com"), publicacionId, MOTIVO).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, publicacionId))
                .isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("AC4: el anfitrión no puede activar su publicación bloqueada y se le indica contactar a soporte")
    void elAnfitrionNoActivaLaBloqueada() throws Exception {
        bloquear(iniciarSesion("rosa@correo.com"), publicacionId, MOTIVO).andExpect(status().isOk());
        String anfitrion = iniciarSesion("luis@correo.com");

        mvc.perform(post("/api/v1/publicaciones/{id}/activar", publicacionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anfitrion))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Esta publicación fue bloqueada por un administrador. Contacta a soporte para revisarla."));
        // En sus publicaciones la ve bloqueada, con el motivo.
        mvc.perform(get("/api/v1/publicaciones/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + anfitrion))
                .andExpect(jsonPath("$[0].estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$[0].motivoBloqueo").value(MOTIVO));
    }

    @Test
    @DisplayName("El buscador de moderación encuentra por título, literal y sin mayúsculas, solo activas, pausadas y bloqueadas")
    void elBuscadorDeModeracionFiltraPorTitulo() throws Exception {
        Long pausada = publicacionActiva("Casa 100% campestre");
        jdbc.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", pausada);
        Long borrador = publicacionActiva("Casa en borrador");
        jdbc.update("UPDATE publicacion SET estado = 'BORRADOR' WHERE id = ?", borrador);
        String admin = iniciarSesion("rosa@correo.com");
        bloquear(admin, publicacionId, MOTIVO).andExpect(status().isOk());

        moderacion(admin, "CASA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(pausada.intValue()))
                .andExpect(jsonPath("$[0].estado").value("PAUSADA"))
                .andExpect(jsonPath("$[1].id").value(publicacionId.intValue()))
                .andExpect(jsonPath("$[1].estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$[1].motivoBloqueo").value(MOTIVO))
                .andExpect(jsonPath("$[1].ciudad").value("Santa Marta"))
                .andExpect(jsonPath("$[1].anfitrionId").value(anfitrionId.intValue()));
        // "%" se busca literal: no es un comodín.
        moderacion(admin, "100%").andExpect(jsonPath("$.length()").value(1));
        moderacion(admin, "0%c").andExpect(jsonPath("$.length()").value(0));
        moderacion(iniciarSesion("luis@correo.com"), "").andExpect(status().isForbidden());
    }

    private ResultActions bloquear(String token, Long id, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/admin/publicaciones/{id}/bloquear", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("motivo", motivo))));
    }

    private ResultActions moderacion(String token, String texto) throws Exception {
        return mvc.perform(get("/api/v1/admin/publicaciones").param("texto", texto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions buscar(String token) throws Exception {
        return mvc.perform(get("/api/v1/busqueda").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("ciudad", "Santa Marta").param("llegada", LLEGADA.toString())
                .param("salida", SALIDA.toString()));
    }

    private Long sembrarReserva(Long publicacion, String estado, LocalDate llegada, LocalDate salida) {
        boolean pagada = !"PENDIENTE".equals(estado);
        return jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en,
                    estado_pago, transaccion_id)
                VALUES (?, ?, ?, ?, ?, 4, 400000, 30000, 40000, 0, 0, 470000, now(), ?, ?)
                RETURNING id
                """, Long.class, publicacion, huespedId, llegada, salida, estado, pagada ? "APROBADO" : "SIN_PAGO",
                pagada ? "sim_" + UUID.randomUUID() : null);
    }

    private String estadoReserva(Long id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    // ACTIVA con precio y un periodo disponible que cubre las fechas de las pruebas, en Santa Marta.
    private Long publicacionActiva(String titulo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("titulo", titulo, "ciudad", "Santa Marta"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = 'ACTIVA', precio_noche = 100000, tarifa_limpieza = 30000, "
                + "capacidad = 4 WHERE id = ?", id);
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)",
                id, LocalDate.now().minusDays(1), LocalDate.now().plusDays(90));
        return id;
    }

    private String iniciarSesion(String correo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("correo", correo, "contrasena", CONTRASENA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
