package co.edu.unbosque.marketplace.reservas.controllers;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
 * Historial de reservas del huésped (SOF-35): token → autorización → servicio → PostgreSQL. Las reservas
 * pasadas se insertan con SQL, porque la API no deja reservar con llegada anterior a hoy.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HistorialReservasIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

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
    private Long ana;
    private Long carla;

    @BeforeEach
    void preparar() throws Exception {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        ana = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA),
                Rol.HUESPED)).getId();
        carla = usuarios.save(Usuario.nuevo("Carla Ruiz", "carla@correo.com", codificador.encode(CONTRASENA),
                Rol.HUESPED)).getId();
        publicacionId = publicacionActiva();
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("AC1: mis reservas trae las futuras y las pasadas con su estado, fechas, total y el alojamiento")
    void listaLasFuturasYLasPasadas() throws Exception {
        long pasadaVieja = insertar(ana, HOY.minusDays(40), HOY.minusDays(37), "CONFIRMADA", "APROBADO", "sim_1",
                "300000.00");
        long pasadaReciente = insertar(ana, HOY.minusDays(10), HOY.minusDays(8), "CANCELADA", "RECHAZADO", null,
                "200000.00");
        long lejana = insertar(ana, HOY.plusDays(30), HOY.plusDays(33), "CONFIRMADA", "APROBADO", "sim_2",
                "330000.00");
        long cercana = insertar(ana, HOY.plusDays(5), HOY.plusDays(7), "CONFIRMADA", "APROBADO", "sim_3",
                "220000.00");

        mias(iniciarSesion("ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras[*].id").value(contains((int) cercana, (int) lejana)))
                .andExpect(jsonPath("$.pasadas[*].id").value(contains((int) pasadaReciente, (int) pasadaVieja)))
                .andExpect(jsonPath("$.futuras[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.futuras[0].llegada").value(HOY.plusDays(5).toString()))
                .andExpect(jsonPath("$.futuras[0].salida").value(HOY.plusDays(7).toString()))
                .andExpect(jsonPath("$.futuras[0].noches").value(2))
                .andExpect(jsonPath("$.futuras[0].total").value(220000.00))
                .andExpect(jsonPath("$.futuras[0].publicacionId").value(publicacionId))
                .andExpect(jsonPath("$.futuras[0].titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$.pasadas[0].estado").value("CANCELADA"))
                .andExpect(jsonPath("$.pasadas[0].total").value(200000.00));
    }

    @Test
    @DisplayName("Una reserva que termina hoy todavía está entre las futuras")
    void unaReservaQueTerminaHoyEsFutura() throws Exception {
        long terminaHoy = insertar(ana, HOY.minusDays(2), HOY, "CONFIRMADA", "APROBADO", "sim_4", "200000.00");

        mias(iniciarSesion("ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras[*].id").value(contains((int) terminaHoy)))
                .andExpect(jsonPath("$.pasadas").value(hasSize(0)));
    }

    @Test
    @DisplayName("AC3: en mis reservas no aparecen las reservas de otros huéspedes")
    void noAparecenReservasDeOtros() throws Exception {
        long deAna = insertar(ana, HOY.plusDays(5), HOY.plusDays(7), "CONFIRMADA", "APROBADO", "sim_5", "220000.00");
        long deCarla = insertar(carla, HOY.plusDays(20), HOY.plusDays(22), "CONFIRMADA", "APROBADO", "sim_6",
                "220000.00");
        long pasadaDeCarla = insertar(carla, HOY.minusDays(9), HOY.minusDays(7), "CONFIRMADA", "APROBADO", "sim_7",
                "220000.00");

        mias(iniciarSesion("ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras[*].id").value(contains((int) deAna)))
                .andExpect(jsonPath("$.pasadas").value(hasSize(0)));
        mias(iniciarSesion("carla@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras[*].id").value(contains((int) deCarla)))
                .andExpect(jsonPath("$.pasadas[*].id").value(contains((int) pasadaDeCarla)));
    }

    @Test
    @DisplayName("Una PENDIENTE cuyo apartado ya venció se muestra VENCIDA aunque la tarea aún no la haya pasado")
    void unaPendienteConElApartadoAgotadoSeMuestraVencida() throws Exception {
        long agotada = insertarPendiente(ana, HOY.plusDays(5), HOY.plusDays(7), Instant.now().minus(1, ChronoUnit.MINUTES));
        long vigente = insertarPendiente(ana, HOY.plusDays(10), HOY.plusDays(12), Instant.now().plus(10, ChronoUnit.MINUTES));

        mias(iniciarSesion("ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras[*].id").value(contains((int) agotada, (int) vigente)))
                .andExpect(jsonPath("$.futuras[0].estado").value("VENCIDA"))
                .andExpect(jsonPath("$.futuras[1].estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("Si el anfitrión pausó la publicación, la reserva sigue mostrando su alojamiento")
    void unaPublicacionPausadaSigueApareciendo() throws Exception {
        insertar(ana, HOY.plusDays(5), HOY.plusDays(7), "CONFIRMADA", "APROBADO", "sim_8", "220000.00");
        jdbc.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", publicacionId);

        mias(iniciarSesion("ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras[0].titulo").value("Casa en la playa"));
    }

    @Test
    @DisplayName("Sin reservas, las dos listas vienen vacías; el anfitrión recibe 403 y sin token 401")
    void sinReservasYAccesoPorRol() throws Exception {
        mias(iniciarSesion("ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.futuras").value(hasSize(0)))
                .andExpect(jsonPath("$.pasadas").value(hasSize(0)));
        mias(iniciarSesion("luis@correo.com")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservas/mias")).andExpect(status().isUnauthorized());
    }

    private ResultActions mias(String token) throws Exception {
        return mvc.perform(get("/api/v1/reservas/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private long insertar(Long huespedId, LocalDate llegada, LocalDate salida, String estado, String estadoPago,
            String transaccionId, String total) {
        return insertarFila(huespedId, llegada, salida, estado, estadoPago, transaccionId, new BigDecimal(total),
                Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private long insertarPendiente(Long huespedId, LocalDate llegada, LocalDate salida, Instant venceEn) {
        return insertarFila(huespedId, llegada, salida, "PENDIENTE", "SIN_PAGO", null, new BigDecimal("220000.00"),
                venceEn);
    }

    private long insertarFila(Long huespedId, LocalDate llegada, LocalDate salida, String estado, String estadoPago,
            String transaccionId, BigDecimal total, Instant venceEn) {
        int noches = (int) ChronoUnit.DAYS.between(llegada, salida);
        return jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en,
                    estado_pago, transaccion_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 30000, 0, 0, 0, ?, ?, ?, ?) RETURNING id
                """, Long.class, publicacionId, huespedId, llegada, salida, estado, noches,
                total.subtract(new BigDecimal("30000")), total, Timestamp.from(venceEn), estadoPago, transaccionId);
    }

    // Igual que PagoReservaIntegracionTest: ACTIVA con precio fijo y un periodo que cubre las fechas futuras.
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
