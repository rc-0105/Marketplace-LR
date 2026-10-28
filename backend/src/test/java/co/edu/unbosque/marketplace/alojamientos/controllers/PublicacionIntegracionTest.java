package co.edu.unbosque.marketplace.alojamientos.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre la creación del borrador: token → autorización → validación → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicacionIntegracionTest {

    private static final String RUTA = "/api/v1/publicaciones";
    private static final String CONTRASENA = "Segura#2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    private Usuario anfitrion;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        anfitrion = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com",
                codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    @Test
    @DisplayName("Un anfitrión crea el borrador: responde 201 y queda guardado a su nombre")
    void anfitrionCreaBorrador() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "tipo": "CASA", "capacidad": 4}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.camposFaltantes[0]").value("descripcion"));

        assertThat(publicaciones.count()).isEqualTo(1);
        Publicacion guardada = publicaciones.findAll().get(0);
        assertThat(guardada.getAnfitrionId()).isEqualTo(anfitrion.getId());
    }

    @Test
    @DisplayName("Un huésped no puede crear publicaciones: responde 403 y no guarda nada")
    void huespedRecibe403() throws Exception {
        String token = iniciarSesion("ana@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa"}
                """).andExpect(status().isForbidden());

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Sin token responde 401 y no guarda nada")
    void sinTokenRecibe401() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"titulo": "Casa en la playa"}
                        """))
                .andExpect(status().isUnauthorized());

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Sin título responde 400 y no guarda nada")
    void sinTituloRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "  "}
                """).andExpect(status().isBadRequest());

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Con capacidad cero responde 400, señala el campo capacidad y no guarda nada")
    void capacidadCeroRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "capacidad": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.capacidad").value("La capacidad debe ser mayor que cero."));

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Con precio por noche negativo responde 400, señala el campo precioNoche y no guarda nada")
    void precioNegativoRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "precioNoche": -1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precioNoche").value("El precio por noche debe ser mayor que cero."));

        assertThat(publicaciones.count()).isZero();
    }

    @Test
    @DisplayName("Con un tipo inexistente responde 400 con el mensaje del tipo")
    void tipoInexistenteRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");

        crear(token, """
                {"titulo": "Casa en la playa", "tipo": "CASTILLO"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El tipo debe ser CASA, APARTAMENTO o HABITACION."));

        assertThat(publicaciones.count()).isZero();
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
