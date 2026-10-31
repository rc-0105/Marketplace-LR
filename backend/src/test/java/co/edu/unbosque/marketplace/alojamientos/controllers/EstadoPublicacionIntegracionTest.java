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

/** Activación, pausa y listado de publicaciones (SOF-22): token → autorización → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class EstadoPublicacionIntegracionTest {

    private static final String RUTA = "/api/v1/publicaciones";
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
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // Las publicaciones referencian al usuario: si quedan, los tests de otros
    // módulos no pueden borrar sus usuarios en su propia preparación.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Activar un borrador completo lo deja en PENDIENTE_REVISION y así aparece en mis publicaciones")
    void activarBorradorCompletoLoEnviaARevision() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearCompleta(token);

        activar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));

        assertThat(estadoGuardado(id)).isEqualTo("PENDIENTE_REVISION");
        mvc.perform(get(RUTA + "/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.intValue()))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE_REVISION"));
    }

    @Test
    @DisplayName("Activar un borrador incompleto responde 422 con exactamente los campos que faltan")
    void activarBorradorIncompletoListaLoQueFalta() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idDe(crear(token, """
                {"titulo": "Casa en la playa", "ciudad": "Santa Marta", "tipo": %d, "capacidad": 4,
                 "precioNoche": 250000, "imagenes": ["https://imagenes.com/1.jpg"]}
                """.formatted(idTipo("Casa"))));

        activar(token, id)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Faltan campos para activar la publicación."))
                .andExpect(jsonPath("$.camposFaltantes.length()").value(4))
                .andExpect(jsonPath("$.camposFaltantes[0]").value("descripcion"))
                .andExpect(jsonPath("$.camposFaltantes[1]").value("direccion"))
                .andExpect(jsonPath("$.camposFaltantes[2]").value("servicios"))
                .andExpect(jsonPath("$.camposFaltantes[3]").value("reglas"));

        assertThat(estadoGuardado(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Una publicación activa se pausa y al reactivarla vuelve a ACTIVA")
    void pausarYReactivar() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearCompleta(token);
        // La aprobación del administrador llega con SOF-23; aquí se simula en la base.
        jdbc.update("UPDATE publicacion SET estado = 'ACTIVA' WHERE id = ?", id);

        pausar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADA"));
        assertThat(estadoGuardado(id)).isEqualTo("PAUSADA");

        activar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        assertThat(estadoGuardado(id)).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Pausar un borrador responde 409 y la publicación no cambia")
    void pausarBorradorRecibe409() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearCompleta(token);

        pausar(token, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Solo se puede pausar una publicación activa."));

        assertThat(estadoGuardado(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Ni otro anfitrión ni un huésped pueden activar o pausar la publicación: responde 403")
    void ajenoYHuespedReciben403() throws Exception {
        Long id = crearCompleta(iniciarSesion("luis@correo.com"));
        String otroAnfitrion = iniciarSesion("marta@correo.com");
        String huesped = iniciarSesion("ana@correo.com");

        activar(otroAnfitrion, id).andExpect(status().isForbidden());
        pausar(otroAnfitrion, id).andExpect(status().isForbidden());
        activar(huesped, id).andExpect(status().isForbidden());
        pausar(huesped, id).andExpect(status().isForbidden());

        assertThat(estadoGuardado(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Sin token responde 401")
    void sinTokenRecibe401() throws Exception {
        Long id = crearCompleta(iniciarSesion("luis@correo.com"));

        mvc.perform(post(RUTA + "/" + id + "/activar")).andExpect(status().isUnauthorized());

        assertThat(estadoGuardado(id)).isEqualTo("BORRADOR");
    }

    private Long crearCompleta(String token) throws Exception {
        return idDe(crear(token, """
                {"titulo": "Casa en la playa", "descripcion": "Frente al mar", "ciudad": "Santa Marta",
                 "direccion": "Calle 1 # 2-3", "tipo": %d, "capacidad": 4, "precioNoche": 250000,
                 "servicios": [%d], "reglas": "No se admiten fiestas",
                 "imagenes": ["https://imagenes.com/1.jpg"]}
                """.formatted(idTipo("Casa"), idServicio("Wifi"))));
    }

    private Long idDe(ResultActions creada) throws Exception {
        String respuesta = creada.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private String estadoGuardado(Long id) {
        return jdbc.queryForObject("SELECT estado FROM publicacion WHERE id = ?", String.class, id);
    }

    private ResultActions activar(String token, Long id) throws Exception {
        return mvc.perform(post(RUTA + "/" + id + "/activar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions pausar(String token, Long id) throws Exception {
        return mvc.perform(post(RUTA + "/" + id + "/pausar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
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

    private ResultActions crear(String token, String cuerpo) throws Exception {
        return mvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }
}
