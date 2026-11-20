package co.edu.unbosque.marketplace.reservas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** Confirmación de una reserva (SOF-33): token → autorización → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class ReservaIntegracionTest {

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

    @BeforeEach
    void preparar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Carla Ruiz", "carla@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("AC1: con la publicación activa y una cotización vigente, confirma la reserva PENDIENTE con su desglose")
    void confirmaLaReservaConSuDesglose() throws Exception {
        Long id = publicacion("ACTIVA");

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.publicacionId").value(id.intValue()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.noches").value(4))
                .andExpect(jsonPath("$.precioBase").value(400000.00))
                .andExpect(jsonPath("$.tarifaLimpieza").value(30000.00))
                .andExpect(jsonPath("$.tarifaServicio").value(40000.00))
                .andExpect(jsonPath("$.total").value(470000.00));
        assertThat(reservasGuardadas(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC2: si otro huésped ya reservó esas fechas, la confirmación detecta el cruce y responde 409")
    void detectaElCruceConOtraReservaYRechaza() throws Exception {
        Long id = publicacion("ACTIVA");
        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isCreated());

        reservar(iniciarSesion("carla@correo.com"), id, LLEGADA.plusDays(1), SALIDA.plusDays(1), "[]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esas fechas ya no están disponibles: alguien más reservó primero."));

        assertThat(reservasGuardadas(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC3: de varias solicitudes simultáneas con fechas superpuestas, solo una queda confirmada")
    void solicitudesSimultaneasSoloConfirmanUna() throws Exception {
        Long id = publicacion("ACTIVA");
        String token = iniciarSesion("ana@correo.com");
        int solicitudes = 6;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(solicitudes);
        try {
            List<Future<Integer>> estados = new ArrayList<>();
            for (int i = 0; i < solicitudes; i++) {
                LocalDate inicio = LLEGADA.plusDays(i);
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return reservar(token, id, inicio, inicio.plusDays(10), "[]")
                            .andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> obtenidos = new ArrayList<>();
            for (Future<Integer> estado : estados) {
                obtenidos.add(estado.get(30, TimeUnit.SECONDS));
            }

            assertThat(obtenidos).containsOnly(201, 409);
            assertThat(obtenidos).filteredOn(estado -> estado == 201).hasSize(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(reservasGuardadas(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC4: si un servicio adicional elegido ya no existe, pide recalcular la cotización y no reserva")
    void servicioAdicionalInexistenteExigeRecotizar() throws Exception {
        Long id = publicacion("ACTIVA");
        Long adicional = servicioAdicional(id, "Desayuno", 15000);
        jdbc.update("DELETE FROM servicio_adicional WHERE id = ?", adicional);

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[" + adicional + "]")
                .andExpect(status().isBadRequest());
        assertThat(reservasGuardadas(id)).isZero();
    }

    @Test
    @DisplayName("AC5: si la publicación se pausó después de cotizar, la confirmación la rechaza")
    void publicacionPausadaDespuesDeCotizarSeRechaza() throws Exception {
        Long id = publicacion("ACTIVA");
        jdbc.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", id);

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isNotFound());
        assertThat(reservasGuardadas(id)).isZero();
    }

    @Test
    @DisplayName("Una publicación en modo APROBACION rechaza la reserva: SOF-33 solo cubre la confirmación inmediata")
    void modoAprobacionRechazaLaReserva() throws Exception {
        Long id = publicacion("ACTIVA");
        jdbc.update("UPDATE publicacion SET modo_reserva = 'APROBACION' WHERE id = ?", id);

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Esta publicación requiere aprobación del anfitrión; todavía no está soportado."));
        assertThat(reservasGuardadas(id)).isZero();
    }

    @Test
    @DisplayName("Reintentar con las mismas fechas devuelve la misma reserva en vez de duplicarla")
    void reintentarConLasMismasFechasNoDuplica() throws Exception {
        Long id = publicacion("ACTIVA");
        String token = iniciarSesion("ana@correo.com");

        int primeraId = idDe(reservar(token, id, LLEGADA, SALIDA, "[]"));
        reservar(token, id, LLEGADA, SALIDA, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(primeraId));

        assertThat(reservasGuardadas(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-4: la reserva PENDIENTE aparta las noches 15 minutos y lo guarda en vence_en")
    void laReservaApartaLasNochesQuinceMinutos() throws Exception {
        Long id = publicacion("ACTIVA");

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isCreated());

        Long minutos = jdbc.queryForObject(
                "SELECT ROUND(EXTRACT(EPOCH FROM (vence_en - creado_en)) / 60) FROM reserva WHERE publicacion_id = ?",
                Long.class, id);
        assertThat(minutos).isEqualTo(15);
    }

    @Test
    @DisplayName("Un apartado vencido libera las noches: otro huésped reserva y la anterior queda VENCIDA")
    void unApartadoVencidoLiberaLasNoches() throws Exception {
        Long id = publicacion("ACTIVA");
        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isCreated());
        jdbc.update("UPDATE reserva SET vence_en = now() - interval '1 minute' WHERE publicacion_id = ?", id);

        reservar(iniciarSesion("carla@correo.com"), id, LLEGADA.plusDays(1), SALIDA.plusDays(1), "[]")
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForList("SELECT estado FROM reserva WHERE publicacion_id = ? ORDER BY id",
                String.class, id)).containsExactly("VENCIDA", "PENDIENTE");
    }

    @Test
    @DisplayName("Mientras el apartado vale, el anfitrión no puede eliminar el periodo; vencido, sí")
    void unApartadoVigenteBloqueaElPeriodoYVencidoNo() throws Exception {
        Long id = publicacion("ACTIVA");
        Long periodoId = jdbc.queryForObject("SELECT id FROM periodo_disponible WHERE publicacion_id = ?", Long.class,
                id);
        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isCreated());
        String anfitrion = iniciarSesion("luis@correo.com");

        mvc.perform(delete("/api/v1/disponibilidad/" + periodoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anfitrion))
                .andExpect(status().isConflict());

        jdbc.update("UPDATE reserva SET vence_en = now() - interval '1 minute' WHERE publicacion_id = ?", id);
        mvc.perform(delete("/api/v1/disponibilidad/" + periodoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anfitrion))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Sin ningún periodo disponible, la reserva se rechaza con 409 y motivo FUERA_DE_PERIODO")
    void sinPeriodoDisponibleSeRechaza() throws Exception {
        Long id = publicacionSinPeriodo("ACTIVA");

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo").value("FUERA_DE_PERIODO"));
        assertThat(reservasGuardadas(id)).isZero();
    }

    @Test
    @DisplayName("Una estancia que cruza dos periodos contiguos no cabe en UNO solo y se rechaza")
    void estanciaQueCruzaDosPeriodosSeRechaza() throws Exception {
        Long id = publicacionSinPeriodo("ACTIVA");
        periodo(id, LLEGADA.minusDays(2), LLEGADA.plusDays(2));
        periodo(id, LLEGADA.plusDays(2), LLEGADA.plusDays(10));

        reservar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo").value("FUERA_DE_PERIODO"));
        assertThat(reservasGuardadas(id)).isZero();
    }

    @Test
    @DisplayName("Una llegada anterior a hoy responde 400 y no reserva")
    void llegadaPasadaSeRechaza() throws Exception {
        Long id = publicacion("ACTIVA");

        reservar(iniciarSesion("ana@correo.com"), id, LocalDate.now().minusDays(3), LocalDate.now().plusDays(1), "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha de llegada no puede ser anterior a hoy."));
        assertThat(reservasGuardadas(id)).isZero();
    }

    @Test
    @DisplayName("Sin fechas ni publicación, responde 400 con el error de cada campo")
    void rechazaCamposFaltantes() throws Exception {
        mvc.perform(post("/api/v1/reservas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.publicacionId").value("Indica la publicación que quieres reservar"))
                .andExpect(jsonPath("$.errores.llegada").value("Indica la fecha de llegada"))
                .andExpect(jsonPath("$.errores.salida").value("Indica la fecha de salida"));
    }

    @Test
    @DisplayName("Solo el huésped reserva: el anfitrión recibe 403 y sin token 401")
    void soloElHuespedReserva() throws Exception {
        Long id = publicacion("ACTIVA");

        reservar(iniciarSesion("luis@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(id, LLEGADA, SALIDA, "[]")))
                .andExpect(status().isUnauthorized());
        assertThat(reservasGuardadas(id)).isZero();
    }

    private ResultActions reservar(String token, Long id, LocalDate llegada, LocalDate salida, String adicionales)
            throws Exception {
        return mvc.perform(post("/api/v1/reservas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(id, llegada, salida, adicionales)));
    }

    private String cuerpo(Long id, LocalDate llegada, LocalDate salida, String adicionales) {
        return """
                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionalesIds": %s}
                """.formatted(id, llegada, salida, adicionales);
    }

    // Con un periodo disponible que cubre todas las fechas de las pruebas (hoy + 60 días).
    private Long publicacion(String estado) throws Exception {
        Long id = publicacionSinPeriodo(estado);
        periodo(id, LocalDate.now().minusDays(1), LocalDate.now().plusDays(60));
        return id;
    }

    private void periodo(Long publicacionId, LocalDate inicio, LocalDate fin) {
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)",
                publicacionId, inicio, fin);
    }

    // Crea la publicación por la API y fija estado, precio y limpieza directo: ACTIVA solo se
    // alcanza con la aprobación del administrador, que no es lo que se prueba aquí.
    private Long publicacionSinPeriodo(String estado) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET estado = ?, precio_noche = 100000, tarifa_limpieza = 30000 WHERE id = ?",
                estado, id);
        return id;
    }

    private Long servicioAdicional(Long publicacionId, String nombre, int costo) {
        return jdbc.queryForObject(
                "INSERT INTO servicio_adicional (publicacion_id, nombre, costo) VALUES (?, ?, ?) RETURNING id",
                Long.class, publicacionId, nombre, costo);
    }

    private int idDe(ResultActions resultado) throws Exception {
        String respuesta = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asInt();
    }

    private Integer reservasGuardadas(Long publicacionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE publicacion_id = ?", Integer.class,
                publicacionId);
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
