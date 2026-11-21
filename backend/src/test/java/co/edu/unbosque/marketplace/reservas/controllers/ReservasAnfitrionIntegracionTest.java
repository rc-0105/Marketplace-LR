package co.edu.unbosque.marketplace.reservas.controllers;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * Listado de reservas del anfitrión (SOF-36): token → autorización → servicio → PostgreSQL, igual que
 * {@code HistorialReservasIntegracionTest} del lado del huésped.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservasAnfitrionIntegracionTest {

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

    private Long ana;
    private Long publicacionUno;
    private Long publicacionDos;
    private Long publicacionDeMarco;

    @BeforeEach
    void preparar() throws Exception {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Marco Díaz", "marco@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        ana = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA),
                Rol.HUESPED)).getId();
        publicacionUno = crearPublicacion("luis@correo.com", "Casa Uno");
        publicacionDos = crearPublicacion("luis@correo.com", "Casa Dos");
        publicacionDeMarco = crearPublicacion("marco@correo.com", "Casa de Marco");
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("AC1: el anfitrión ve todas las reservas de sus publicaciones, en cualquier estado, y no las de otro")
    void listaTodasLasReservasDeSusPublicaciones() throws Exception {
        long enP1 = insertar(publicacionUno, HOY.plusDays(5), HOY.plusDays(7), "CONFIRMADA");
        long enP2 = insertar(publicacionDos, HOY.minusDays(10), HOY.minusDays(8), "CANCELADA");
        insertar(publicacionDeMarco, HOY.plusDays(2), HOY.plusDays(4), "CONFIRMADA");

        anfitrion(iniciarSesion("luis@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(2)))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder((int) enP1, (int) enP2)))
                .andExpect(jsonPath("$[*].huespedNombre").value(containsInAnyOrder("Ana Gómez", "Ana Gómez")));
    }

    @Test
    @DisplayName("Un anfitrión sin publicaciones recibe 200 con lista vacía, no 404")
    void sinPublicacionesRespondeListaVacia() throws Exception {
        usuarios.save(Usuario.nuevo("Sin Casa", "sincasa@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));

        anfitrion(iniciarSesion("sincasa@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(0)));
    }

    @Test
    @DisplayName("Todos los estados son visibles: PENDIENTE, CONFIRMADA, CANCELADA y VENCIDA")
    void todosLosEstadosSonVisibles() throws Exception {
        insertar(publicacionUno, HOY.plusDays(1), HOY.plusDays(2), "PENDIENTE");
        insertar(publicacionUno, HOY.plusDays(3), HOY.plusDays(4), "CONFIRMADA");
        insertar(publicacionUno, HOY.plusDays(5), HOY.plusDays(6), "CANCELADA");
        insertar(publicacionUno, HOY.plusDays(7), HOY.plusDays(8), "VENCIDA");

        anfitrion(iniciarSesion("luis@correo.com"), publicacionUno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(4)))
                .andExpect(jsonPath("$[*].estado")
                        .value(containsInAnyOrder("PENDIENTE", "CONFIRMADA", "CANCELADA", "VENCIDA")));
    }

    @Test
    @DisplayName("Orden: las reservas se devuelven por llegada ascendente, sin separar pasadas de futuras")
    void ordenaPorLlegadaAscendenteSinSepararPasadasYFuturas() throws Exception {
        long pasada = insertar(publicacionUno, HOY.minusDays(30), HOY.minusDays(28), "CONFIRMADA");
        long cercana = insertar(publicacionUno, HOY.plusDays(2), HOY.plusDays(4), "CONFIRMADA");
        long lejana = insertar(publicacionUno, HOY.plusDays(20), HOY.plusDays(22), "CONFIRMADA");

        anfitrion(iniciarSesion("luis@correo.com"), publicacionUno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains((int) pasada, (int) cercana, (int) lejana)));
    }

    @Test
    @DisplayName("AC2: con publicacionId, solo se devuelven las reservas de esa publicación")
    void filtraPorPublicacionIdPropia() throws Exception {
        long enP1 = insertar(publicacionUno, HOY.plusDays(5), HOY.plusDays(7), "CONFIRMADA");
        insertar(publicacionDos, HOY.plusDays(3), HOY.plusDays(4), "CONFIRMADA");

        anfitrion(iniciarSesion("luis@correo.com"), publicacionUno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(enP1))
                .andExpect(jsonPath("$[0].publicacionId").value(publicacionUno));
    }

    @Test
    @DisplayName("AC3: un publicacionId que no existe responde 404, nunca 403")
    void publicacionIdInexistenteResponde404() throws Exception {
        anfitrion(iniciarSesion("luis@correo.com"), 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa publicación."));
    }

    @Test
    @DisplayName("AC3: una publicación de otro anfitrión responde 403")
    void publicacionDeOtroAnfitrionResponde403() throws Exception {
        anfitrion(iniciarSesion("luis@correo.com"), publicacionDeMarco)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Quien no tiene el rol ANFITRION recibe 403, aunque esté autenticado")
    void noAnfitrionRecibe403() throws Exception {
        anfitrion(iniciarSesion("ana@correo.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token, /anfitrion responde 401")
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/v1/reservas/anfitrion")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La fila del huésped solo trae su nombre: ni correo ni teléfono aparecen en la respuesta")
    void noExponeCorreoNiTelefonoDelHuesped() throws Exception {
        insertar(publicacionUno, HOY.plusDays(5), HOY.plusDays(7), "CONFIRMADA");

        anfitrion(iniciarSesion("luis@correo.com"), publicacionUno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].huespedNombre").value("Ana Gómez"))
                .andExpect(jsonPath("$[0].correo").doesNotExist())
                .andExpect(jsonPath("$[0].huespedCorreo").doesNotExist())
                .andExpect(jsonPath("$[0].telefono").doesNotExist())
                .andExpect(jsonPath("$[0].huespedTelefono").doesNotExist())
                .andExpect(jsonPath("$[0].huespedId").doesNotExist());
    }

    @Test
    @DisplayName("/anfitrion no queda a la sombra de GET /{id}: resuelve al listado, no a un intento de parsear un id")
    void anfitrionNoEsOcultadoPorGetPorId() throws Exception {
        anfitrion(iniciarSesion("luis@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private ResultActions anfitrion(String token) throws Exception {
        return mvc.perform(get("/api/v1/reservas/anfitrion").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions anfitrion(String token, Long publicacionId) throws Exception {
        if (publicacionId == null) {
            return anfitrion(token);
        }
        return mvc.perform(get("/api/v1/reservas/anfitrion").param("publicacionId", String.valueOf(publicacionId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // estado_pago queda SIN_PAGO siempre: esta prueba no depende del pago, y 'APROBADO' exigiría además
    // una transaccion_id por la restricción reserva_transaccion_check (SOF-34). vence_en solo importa para
    // PENDIENTE (estadoActual la recalcula a VENCIDA si ya pasó); las demás lo ignoran.
    private long insertar(Long publicacionId, LocalDate llegada, LocalDate salida, String estado) {
        int noches = (int) ChronoUnit.DAYS.between(llegada, salida);
        Instant venceEn = "PENDIENTE".equals(estado) ? Instant.now().plus(10, ChronoUnit.MINUTES)
                : Instant.now().minus(1, ChronoUnit.DAYS);
        return jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en)
                VALUES (?, ?, ?, ?, ?, ?, 200000, 30000, 0, 0, 0, 230000, ?) RETURNING id
                """, Long.class, publicacionId, ana, llegada, salida, estado, noches, Timestamp.from(venceEn));
    }

    // Igual que HistorialReservasIntegracionTest / CancelacionReservaIntegracionTest: una publicación mínima
    // alcanza para que la reserva se pueda insertar y el anfitrión sea su dueño.
    private Long crearPublicacion(String correoAnfitrion, String titulo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion(correoAnfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "%s"}
                                """.formatted(titulo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
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
