package co.edu.unbosque.marketplace.alojamientos.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Revisión administrativa de publicaciones pendientes (SOF-23): token → autorización → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class RevisionPublicacionIntegracionTest {

    private static final String RUTA = "/api/v1/publicaciones";
    private static final String RUTA_ADMIN = "/api/v1/admin/publicaciones";
    private static final String CONTRASENA = "Segura#2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private ServicioRepository catalogo;

    @Autowired
    private TipoAlojamientoRepository tipos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Rosa Díaz", "rosa@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));
    }

    // Las publicaciones referencian al usuario: si quedan, los tests de otros
    // módulos no pueden borrar sus usuarios en su propia preparación.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("El panel lista solo las pendientes, de la que se envió a revisión hace más tiempo a la más reciente")
    void listaPendientesPorAntiguedad() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long reciente = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa reciente"));
        Long antigua = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa antigua"));
        crearCompleta(anfitrion, "Casa en borrador");
        // La antigüedad cuenta desde que se pidió la revisión, no desde que se creó.
        jdbc.update("UPDATE publicacion SET enviada_revision_en = now() - interval '2 days' WHERE id = ?", antigua);

        pendientes(iniciarSesion("rosa@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(antigua.intValue()))
                .andExpect(jsonPath("$[1].id").value(reciente.intValue()))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE_REVISION"));
    }

    @Test
    @DisplayName("Aprobar una pendiente la deja ACTIVA y el anfitrión la ve así en sus publicaciones")
    void aprobarPendienteLaPublica() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long id = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa en la playa"));

        aprobar(iniciarSesion("rosa@correo.com"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        assertThat(estadoGuardado(id)).isEqualTo("ACTIVA");
        mias(anfitrion)
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$[0].observacionesRevision").doesNotExist());
    }

    @Test
    @DisplayName("Devolver una pendiente con observaciones la deja en BORRADOR y el anfitrión ve el motivo")
    void devolverPendienteMuestraElMotivo() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long id = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa en la playa"));

        devolver(iniciarSesion("rosa@correo.com"), id, """
                {"observaciones": "Faltan fotos del baño"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.observacionesRevision").value("Faltan fotos del baño"));

        assertThat(estadoGuardado(id)).isEqualTo("BORRADOR");
        mias(anfitrion)
                .andExpect(jsonPath("$[0].estado").value("BORRADOR"))
                .andExpect(jsonPath("$[0].observacionesRevision").value("Faltan fotos del baño"));
    }

    @Test
    @DisplayName("Devolver sin observaciones responde 400 y la publicación sigue pendiente")
    void devolverSinObservacionesRecibe400() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long id = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa en la playa"));

        devolver(iniciarSesion("rosa@correo.com"), id, """
                {"observaciones": "   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.observaciones").exists());

        assertThat(estadoGuardado(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("Aprobar una publicación que no está pendiente responde 409 y no cambia")
    void aprobarBorradorRecibe409() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long id = crearCompleta(anfitrion, "Casa en la playa");

        aprobar(iniciarSesion("rosa@correo.com"), id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se puede revisar una publicación pendiente de revisión."));

        assertThat(estadoGuardado(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Si no soy administrador, listar, aprobar y devolver responden 403")
    void quienNoEsAdministradorRecibe403() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long id = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa en la playa"));
        String huesped = iniciarSesion("ana@correo.com");

        for (String token : new String[] {anfitrion, huesped}) {
            pendientes(token).andExpect(status().isForbidden());
            aprobar(token, id).andExpect(status().isForbidden());
            devolver(token, id, """
                    {"observaciones": "Motivo"}
                    """).andExpect(status().isForbidden());
        }

        assertThat(estadoGuardado(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("Sin token responde 401")
    void sinTokenRecibe401() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long id = enviarARevision(anfitrion, crearCompleta(anfitrion, "Casa en la playa"));

        mvc.perform(get(RUTA_ADMIN + "/pendientes")).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA_ADMIN + "/" + id + "/aprobar")).andExpect(status().isUnauthorized());

        assertThat(estadoGuardado(id)).isEqualTo("PENDIENTE_REVISION");
    }

    private Long crearCompleta(String token, String titulo) throws Exception {
        String cuerpo = """
                {"titulo": "%s", "descripcion": "Frente al mar", "ciudad": "Santa Marta",
                 "direccion": "Calle 1 # 2-3", "tipo": %d, "capacidad": 4, "precioNoche": 250000,
                 "servicios": [%d], "reglas": "No se admiten fiestas",
                 "imagenes": ["https://imagenes.com/1.jpg"]}
                """.formatted(titulo, idTipo("Casa"), idServicio("Wifi"));
        String respuesta = mvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private Long enviarARevision(String token, Long id) throws Exception {
        mvc.perform(post(RUTA + "/" + id + "/activar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));
        return id;
    }

    private String estadoGuardado(Long id) {
        return jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, id);
    }

    private ResultActions pendientes(String token) throws Exception {
        return mvc.perform(get(RUTA_ADMIN + "/pendientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions mias(String token) throws Exception {
        return mvc.perform(get(RUTA + "/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions aprobar(String token, Long id) throws Exception {
        return mvc.perform(post(RUTA_ADMIN + "/" + id + "/aprobar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions devolver(String token, Long id, String cuerpo) throws Exception {
        return mvc.perform(post(RUTA_ADMIN + "/" + id + "/devolver")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private Long idServicio(String nombre) {
        return catalogo.findAllByOrderByNombreAsc().stream()
                .filter(servicio -> servicio.getNombre().equals(nombre))
                .findFirst().orElseThrow().getId();
    }

    private Long idTipo(String nombre) {
        return tipos.findAllByOrderByNombreAsc().stream()
                .filter(tipo -> tipo.getNombre().equals(nombre))
                .findFirst().orElseThrow().getId();
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
