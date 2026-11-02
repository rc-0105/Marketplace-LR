package co.edu.unbosque.marketplace.disponibilidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Periodos disponibles (SOF-25): token → autorización → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class DisponibilidadIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

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

    // Las reservas llegan con SOF-33: aquí se simula la que bloquea un periodo.
    @MockBean
    private ConsultaReservasPeriodo reservas;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // Los periodos caen en cascada con la publicación, y las publicaciones referencian
    // al usuario: si quedan, los tests de otros módulos no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Registrar un periodo válido responde 201, lo guarda y aparece en el listado del anfitrión")
    void registrarPeriodoValidoQuedaVisible() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);

        registrar(token, publicacionId, HOY.plusDays(10), HOY.plusDays(15))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.publicacionId").value(publicacionId.intValue()))
                .andExpect(jsonPath("$.fechaInicio").value(HOY.plusDays(10).toString()))
                .andExpect(jsonPath("$.fechaFin").value(HOY.plusDays(15).toString()));
        registrar(token, publicacionId, HOY.plusDays(1), HOY.plusDays(3)).andExpect(status().isCreated());

        assertThat(periodosGuardados(publicacionId)).isEqualTo(2);
        mvc.perform(get(rutaPublicacion(publicacionId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fechaInicio").value(HOY.plusDays(1).toString()))
                .andExpect(jsonPath("$[1].fechaInicio").value(HOY.plusDays(10).toString()));
    }

    @Test
    @DisplayName("Si la fecha final no es posterior a la inicial, responde 400 y no guarda nada")
    void rechazaFechaFinalNoPosterior() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);

        registrar(token, publicacionId, HOY.plusDays(5), HOY.plusDays(5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha final debe ser posterior a la fecha inicial."));
        registrar(token, publicacionId, HOY.plusDays(5), HOY.plusDays(2))
                .andExpect(status().isBadRequest());

        assertThat(periodosGuardados(publicacionId)).isZero();
    }

    @Test
    @DisplayName("Sin fechas, responde 400 con el error de cada campo")
    void rechazaFechasFaltantes() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);

        mvc.perform(post(rutaPublicacion(publicacionId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaInicio").value("La fecha inicial es obligatoria."))
                .andExpect(jsonPath("$.errores.fechaFin").value("La fecha final es obligatoria."));
    }

    @Test
    @DisplayName("Un periodo que se cruza con otro de la misma publicación responde 409")
    void rechazaPeriodoQueSeCruza() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);
        registrar(token, publicacionId, HOY.plusDays(10), HOY.plusDays(15)).andExpect(status().isCreated());

        registrar(token, publicacionId, HOY.plusDays(14), HOY.plusDays(20))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El periodo se cruza con otro periodo disponible de la publicación."));
        registrar(token, publicacionId, HOY.plusDays(15), HOY.plusDays(20)).andExpect(status().isCreated());

        assertThat(periodosGuardados(publicacionId)).isEqualTo(2);
    }

    @Test
    @DisplayName("PostgreSQL rechaza un periodo que se cruza aunque no pase por la API, y acepta uno contiguo")
    void laBaseRechazaElCruceDirecto() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);
        registrar(token, publicacionId, HOY.plusDays(10), HOY.plusDays(15)).andExpect(status().isCreated());
        String insertar = "INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insertar, publicacionId, HOY.plusDays(12), HOY.plusDays(20)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("periodo_disponible_sin_cruce");
        jdbc.update(insertar, publicacionId, HOY.plusDays(15), HOY.plusDays(20));

        assertThat(periodosGuardados(publicacionId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Dos solicitudes simultáneas con periodos que se cruzan: una se guarda y la otra responde 409")
    void solicitudesSimultaneasNoDejanPeriodosCruzados() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);
        int solicitudes = 6;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(solicitudes);
        try {
            List<Future<Integer>> estados = new ArrayList<>();
            for (int i = 0; i < solicitudes; i++) {
                LocalDate inicio = HOY.plusDays(10 + i);
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return registrar(token, publicacionId, inicio, inicio.plusDays(10))
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
        assertThat(periodosGuardados(publicacionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin reservas, el anfitrión modifica y elimina su periodo")
    void modificaYEliminaSinReservas() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);
        Long periodoId = idDe(registrar(token, publicacionId, HOY.plusDays(10), HOY.plusDays(15)));

        mvc.perform(put("/api/v1/disponibilidad/" + periodoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(HOY.plusDays(12), HOY.plusDays(20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(periodoId.intValue()))
                .andExpect(jsonPath("$.fechaInicio").value(HOY.plusDays(12).toString()))
                .andExpect(jsonPath("$.fechaFin").value(HOY.plusDays(20).toString()));
        assertThat(jdbc.queryForObject("SELECT fecha_fin FROM periodo_disponible WHERE id = ?", LocalDate.class,
                periodoId)).isEqualTo(HOY.plusDays(20));

        mvc.perform(delete("/api/v1/disponibilidad/" + periodoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(periodosGuardados(publicacionId)).isZero();
    }

    @Test
    @DisplayName("Con una reserva confirmada en el periodo, modificar o eliminar responde 409 e indica la reserva")
    void reservaConfirmadaBloqueaElPeriodo() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);
        Long periodoId = idDe(registrar(token, publicacionId, HOY.plusDays(10), HOY.plusDays(15)));
        when(reservas.reservaConfirmadaEn(eq(publicacionId), any(), any())).thenReturn(Optional.of(12L));

        mvc.perform(put("/api/v1/disponibilidad/" + periodoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(HOY.plusDays(12), HOY.plusDays(20))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El periodo tiene la reserva 12 confirmada; no se puede modificar ni eliminar."))
                .andExpect(jsonPath("$.reservaId").value(12));
        mvc.perform(delete("/api/v1/disponibilidad/" + periodoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reservaId").value(12));

        assertThat(jdbc.queryForObject("SELECT fecha_inicio FROM periodo_disponible WHERE id = ?", LocalDate.class,
                periodoId)).isEqualTo(HOY.plusDays(10));
    }

    @Test
    @DisplayName("Otro anfitrión no ve, registra, modifica ni elimina periodos ajenos: responde 403")
    void otroAnfitrionRecibe403() throws Exception {
        String dueno = iniciarSesion("luis@correo.com");
        String otro = iniciarSesion("marta@correo.com");
        Long publicacionId = crearPublicacion(dueno);
        Long periodoId = idDe(registrar(dueno, publicacionId, HOY.plusDays(10), HOY.plusDays(15)));

        registrar(otro, publicacionId, HOY.plusDays(20), HOY.plusDays(25)).andExpect(status().isForbidden());
        mvc.perform(get(rutaPublicacion(publicacionId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + otro))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/disponibilidad/" + periodoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(HOY.plusDays(12), HOY.plusDays(20))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/disponibilidad/" + periodoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otro))
                .andExpect(status().isForbidden());

        assertThat(periodosGuardados(publicacionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Un huésped recibe 403 y sin token se responde 401")
    void huespedYAnonimoNoGestionanPeriodos() throws Exception {
        String dueno = iniciarSesion("luis@correo.com");
        String huesped = iniciarSesion("ana@correo.com");
        Long publicacionId = crearPublicacion(dueno);

        registrar(huesped, publicacionId, HOY.plusDays(1), HOY.plusDays(2)).andExpect(status().isForbidden());
        mvc.perform(post(rutaPublicacion(publicacionId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(HOY.plusDays(1), HOY.plusDays(2))))
                .andExpect(status().isUnauthorized());

        assertThat(periodosGuardados(publicacionId)).isZero();
    }

    @Test
    @DisplayName("Un periodo o una publicación inexistente responde 404")
    void inexistenteResponde404() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        registrar(token, 999_999L, HOY.plusDays(1), HOY.plusDays(2)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/disponibilidad/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos ese periodo."));
    }

    @Test
    @DisplayName("Eliminar un borrador borra también sus periodos")
    void eliminarBorradorBorraSusPeriodos() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long publicacionId = crearPublicacion(token);
        registrar(token, publicacionId, HOY.plusDays(1), HOY.plusDays(2)).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/publicaciones/" + publicacionId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(periodosGuardados(publicacionId)).isZero();
    }

    private static String rutaPublicacion(Long publicacionId) {
        return "/api/v1/publicaciones/" + publicacionId + "/disponibilidad";
    }

    private static String cuerpo(LocalDate inicio, LocalDate fin) {
        return """
                {"fechaInicio": "%s", "fechaFin": "%s"}
                """.formatted(inicio, fin);
    }

    private ResultActions registrar(String token, Long publicacionId, LocalDate inicio, LocalDate fin)
            throws Exception {
        return mvc.perform(post(rutaPublicacion(publicacionId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(inicio, fin)));
    }

    private Long crearPublicacion(String token) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private Long idDe(ResultActions resultado) throws Exception {
        String respuesta = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private Integer periodosGuardados(Long publicacionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM periodo_disponible WHERE publicacion_id = ?", Integer.class,
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
