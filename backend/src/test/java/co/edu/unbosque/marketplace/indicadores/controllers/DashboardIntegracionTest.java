package co.edu.unbosque.marketplace.indicadores.controllers;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Pruebas HTTP del dashboard de indicadores (SOF-38, RF-41, RF-42). Las publicaciones y reservas se
 * insertan por SQL para fijar estado, ciudad y fecha de creación sin pasar por sus flujos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegracionTest {

    private static final String RUTA = "/api/v1/admin/dashboard";
    private static final String CONTRASENA = "Segura#2026";
    private static final String ADMINISTRADOR = "admin@correo.com";
    private static final String ANFITRION = "luis@correo.com";
    private static final String HUESPED = "ana@correo.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private PasswordEncoder codificador;

    private Long anfitrion;
    private Long huesped;
    private int reservasInsertadas;

    @BeforeEach
    void preparar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        anfitrion = crearUsuario("Luis Pardo", ANFITRION, Rol.ANFITRION);
        huesped = crearUsuario("Ana Gómez", HUESPED, Rol.HUESPED);
        crearUsuario("Admin", ADMINISTRADOR, Rol.ADMINISTRADOR);
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("CA1: sin datos de negocio trae todos los roles y estados, con ceros y sin ciudades")
    void sinDatosTraeCeros() throws Exception {
        consultar("", token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").doesNotExist())
                .andExpect(jsonPath("$.hasta").doesNotExist())
                .andExpect(jsonPath("$.usuariosPorRol.HUESPED").value(1))
                .andExpect(jsonPath("$.usuariosPorRol.ANFITRION").value(1))
                .andExpect(jsonPath("$.usuariosPorRol.ADMINISTRADOR").value(1))
                .andExpect(jsonPath("$.publicacionesActivas").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.PENDIENTE").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.CANCELADA").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.VENCIDA").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.COMPLETADA").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.EN_REVISION_MANUAL").value(0))
                .andExpect(jsonPath("$.ingresosEstimados").value(0))
                .andExpect(jsonPath("$.ciudadesMayorDemanda").value(hasSize(0)));
    }

    @Test
    @DisplayName("CA1: muestra usuarios por rol, publicaciones activas, reservas por estado, ingresos y ciudades")
    void muestraTodosLosIndicadores() throws Exception {
        crearUsuario("Marta Ruiz", "marta@correo.com", Rol.HUESPED);
        Long eliminada = crearUsuario("Pedro Gil", "pedro@correo.com", Rol.HUESPED);
        // Una cuenta eliminada queda anonimizada (usuario_eliminada_anonima_check).
        jdbc.update("""
                UPDATE usuario SET estado = 'ELIMINADA', nombre = 'Usuario eliminado',
                    correo = 'eliminada-' || CAST(id AS TEXT) || '@eliminada.invalid' WHERE id = ?
                """, eliminada);

        Long bogota = crearPublicacion("Bogotá", "ACTIVA");
        Long medellin = crearPublicacion("Medellín", "ACTIVA");
        Long cali = crearPublicacion(" Cali ", "PAUSADA");
        Long sinCiudad = crearPublicacion(null, "ACTIVA");
        crearPublicacion("Pasto", "BORRADOR");

        insertarReserva(bogota, "CONFIRMADA", 230000);
        insertarReserva(bogota, "COMPLETADA", 100000);
        insertarReserva(bogota, "CANCELADA", 50000);
        insertarReserva(medellin, "PENDIENTE", 70000);
        long vencida = insertarReserva(medellin, "PENDIENTE", 80000);
        jdbc.update("UPDATE reserva SET vence_en = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), vencida);
        insertarReserva(cali, "VENCIDA", 90000);
        insertarReserva(cali, "EN_REVISION_MANUAL", 60000);
        insertarReserva(sinCiudad, "CONFIRMADA", 40000);

        consultar("", token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuariosPorRol.HUESPED").value(2))
                .andExpect(jsonPath("$.usuariosPorRol.ANFITRION").value(1))
                .andExpect(jsonPath("$.usuariosPorRol.ADMINISTRADOR").value(1))
                .andExpect(jsonPath("$.publicacionesActivas").value(3))
                .andExpect(jsonPath("$.reservasPorEstado.PENDIENTE").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(2))
                .andExpect(jsonPath("$.reservasPorEstado.CANCELADA").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.VENCIDA").value(2))
                .andExpect(jsonPath("$.reservasPorEstado.COMPLETADA").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.EN_REVISION_MANUAL").value(1))
                .andExpect(jsonPath("$.ingresosEstimados").value(430000.0))
                .andExpect(jsonPath("$.ciudadesMayorDemanda[*].ciudad").value(contains("Bogotá", "Cali", "Medellín")))
                .andExpect(jsonPath("$.ciudadesMayorDemanda[*].reservas").value(contains(3, 2, 2)));
    }

    @Test
    @DisplayName("CA1: el ranking muestra como máximo cinco ciudades y desempata por nombre")
    void rankingLimitadoACincoCiudades() throws Exception {
        for (String ciudad : new String[] {"Tunja", "Neiva", "Ibagué", "Cúcuta", "Armenia", "Pereira"}) {
            insertarReserva(crearPublicacion(ciudad, "ACTIVA"), "CONFIRMADA", 1000);
        }
        insertarReserva(crearPublicacion("Pereira", "ACTIVA"), "CANCELADA", 1000);

        consultar("", token(ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudadesMayorDemanda[*].ciudad")
                        .value(contains("Pereira", "Armenia", "Cúcuta", "Ibagué", "Neiva")))
                .andExpect(jsonPath("$.ciudadesMayorDemanda[*].reservas").value(contains(2, 1, 1, 1, 1)));
    }

    @Test
    @DisplayName("CA2: al confirmarse una reserva nueva cambian el conteo por estado y los ingresos")
    void confirmarReservaActualizaIndicadores() throws Exception {
        Long publicacion = crearPublicacion("Bogotá", "ACTIVA");
        insertarReserva(publicacion, "CONFIRMADA", 230000);
        long nueva = insertarReserva(publicacion, "PENDIENTE", 150000);
        String token = token(ADMINISTRADOR);

        consultar("", token)
                .andExpect(jsonPath("$.reservasPorEstado.PENDIENTE").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(1))
                .andExpect(jsonPath("$.ingresosEstimados").value(230000.0));

        jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA' WHERE id = ?", nueva);

        consultar("", token)
                .andExpect(jsonPath("$.reservasPorEstado.PENDIENTE").value(0))
                .andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(2))
                .andExpect(jsonPath("$.ingresosEstimados").value(380000.0));
    }

    @Test
    @DisplayName("CA3: con un rango de fechas todos los indicadores se recalculan sobre ese rango, en hora de Bogotá")
    void filtraTodosLosIndicadoresPorRango() throws Exception {
        String token = token(ADMINISTRADOR);
        // Bogotá es UTC-5: el 10/11 empieza a las 05:00 UTC y el 12/11 termina el 13/11 a las 05:00 UTC.
        Instant antesDelRango = Instant.parse("2026-11-10T04:59:00Z");
        Instant inicioDelRango = Instant.parse("2026-11-10T05:00:00Z");
        Instant finDelRango = Instant.parse("2026-11-13T04:59:00Z");
        Instant despuesDelRango = Instant.parse("2026-11-13T05:00:00Z");

        fecharUsuario(huesped, inicioDelRango);
        fecharUsuario(anfitrion, antesDelRango);
        jdbc.update("UPDATE usuario SET creado_en = ? WHERE correo = ?", Timestamp.from(despuesDelRango), ADMINISTRADOR);

        Long enRango = crearPublicacion("Bogotá", "ACTIVA");
        fecharPublicacion(enRango, finDelRango);
        Long fueraDeRango = crearPublicacion("Cali", "ACTIVA");
        fecharPublicacion(fueraDeRango, antesDelRango);

        fecharReserva(insertarReserva(fueraDeRango, "CONFIRMADA", 100000), inicioDelRango);
        fecharReserva(insertarReserva(enRango, "COMPLETADA", 200000), finDelRango);
        fecharReserva(insertarReserva(enRango, "CANCELADA", 300000), inicioDelRango);
        fecharReserva(insertarReserva(enRango, "CONFIRMADA", 400000), antesDelRango);
        fecharReserva(insertarReserva(fueraDeRango, "CONFIRMADA", 500000), despuesDelRango);

        consultar("?desde=2026-11-10&hasta=2026-11-12", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-11-10"))
                .andExpect(jsonPath("$.hasta").value("2026-11-12"))
                .andExpect(jsonPath("$.usuariosPorRol.HUESPED").value(1))
                .andExpect(jsonPath("$.usuariosPorRol.ANFITRION").value(0))
                .andExpect(jsonPath("$.usuariosPorRol.ADMINISTRADOR").value(0))
                .andExpect(jsonPath("$.publicacionesActivas").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.COMPLETADA").value(1))
                .andExpect(jsonPath("$.reservasPorEstado.CANCELADA").value(1))
                .andExpect(jsonPath("$.ingresosEstimados").value(300000.0))
                .andExpect(jsonPath("$.ciudadesMayorDemanda[*].ciudad").value(contains("Bogotá", "Cali")))
                .andExpect(jsonPath("$.ciudadesMayorDemanda[*].reservas").value(contains(2, 1)));

        // Con un solo extremo el rango queda abierto por el otro lado.
        consultar("?desde=2026-11-13", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuariosPorRol.ADMINISTRADOR").value(1))
                .andExpect(jsonPath("$.usuariosPorRol.HUESPED").value(0))
                .andExpect(jsonPath("$.ingresosEstimados").value(500000.0));
        consultar("?hasta=2026-11-09", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuariosPorRol.ANFITRION").value(1))
                .andExpect(jsonPath("$.publicacionesActivas").value(1))
                .andExpect(jsonPath("$.ingresosEstimados").value(400000.0));
    }

    @Test
    @DisplayName("RF-42: un rango con desde posterior a hasta responde 400 con un mensaje claro")
    void rangoInvertidoResponde400() throws Exception {
        consultar("?desde=2026-11-13&hasta=2026-11-12", token(ADMINISTRADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha desde no puede ser posterior a la fecha hasta."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?desde=abc", "?hasta=12/11/2026", "?desde=2026-13-45"})
    @DisplayName("RF-42: una fecha mal formada responde 400")
    void fechaMalFormadaResponde400(String consulta) throws Exception {
        consultar(consulta, token(ADMINISTRADOR)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {HUESPED, ANFITRION})
    @DisplayName("CA4: un huésped o un anfitrión recibe 403")
    void otrosRolesReciben403(String correo) throws Exception {
        consultar("", token(correo)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA4: sin sesión responde 401")
    void sinSesionResponde401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    private ResultActions consultar(String consulta, String token) throws Exception {
        return mvc.perform(get(RUTA + consulta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long crearUsuario(String nombre, String correo, Rol rol) {
        return usuarios.save(Usuario.nuevo(nombre, correo, codificador.encode(CONTRASENA), rol)).getId();
    }

    private Long crearPublicacion(String ciudad, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, titulo, ciudad, estado) VALUES (?, 'Casa', ?, ?) RETURNING id
                """, Long.class, anfitrion, ciudad, estado);
    }

    // estado_pago queda SIN_PAGO, como en ReservasAnfitrionIntegracionTest: 'APROBADO' exigiría una
    // transaccion_id. Una PENDIENTE nace con el apartado vigente; las demás ignoran vence_en. Cada
    // reserva ocupa noches distintas para no chocar con la restricción reserva_sin_cruce.
    private long insertarReserva(Long publicacionId, String estado, long total) {
        LocalDate llegada = LocalDate.of(2027, 1, 1).plusDays(3L * reservasInsertadas++);
        Instant venceEn = "PENDIENTE".equals(estado) ? Instant.now().plus(10, ChronoUnit.MINUTES)
                : Instant.now().minus(1, ChronoUnit.DAYS);
        return jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en)
                VALUES (?, ?, ?, ?, ?, 2, ?, 0, 0, 0, 0, ?, ?) RETURNING id
                """, Long.class, publicacionId, huesped, llegada, llegada.plusDays(2), estado, total, total,
                Timestamp.from(venceEn));
    }

    private void fecharUsuario(Long id, Instant fecha) {
        jdbc.update("UPDATE usuario SET creado_en = ? WHERE id = ?", Timestamp.from(fecha), id);
    }

    private void fecharPublicacion(Long id, Instant fecha) {
        jdbc.update("UPDATE publicacion SET creado_en = ? WHERE id = ?", Timestamp.from(fecha), id);
    }

    private void fecharReserva(long id, Instant fecha) {
        jdbc.update("UPDATE reserva SET creado_en = ? WHERE id = ?", Timestamp.from(fecha), id);
    }

    private String token(String correo) throws Exception {
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
