package co.edu.unbosque.marketplace.alojamientos.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre la creación, consulta, edición y eliminación: token → autorización → validación → servicio → PostgreSQL. */
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

    // Las publicaciones referencian al usuario: si quedan, los tests de otros
    // módulos no pueden borrar sus usuarios en su propia preparación.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
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

    @Test
    @DisplayName("El dueño edita su publicación: responde 200, guarda los cambios y registra quién editó y cuándo")
    void duenoEditaSuPublicacion() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idCreado(token);

        editar(token, id, """
                {"titulo": "Casa renovada", "tipo": "APARTAMENTO", "precioNoche": 300000}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa renovada"))
                .andExpect(jsonPath("$.tipo").value("APARTAMENTO"))
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.editadoPor").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.editadoEn").isNotEmpty());

        Publicacion guardada = publicaciones.findById(id).orElseThrow();
        assertThat(guardada.getTitulo()).isEqualTo("Casa renovada");
        assertThat(guardada.getPrecioNoche()).isEqualByComparingTo("300000");
        assertThat(guardada.getEditadoPor()).isEqualTo(anfitrion.getId());
        assertThat(guardada.getEditadoEn()).isNotNull();
    }

    @Test
    @DisplayName("El anfitrión lista solo sus publicaciones, la más reciente primero")
    void anfitrionListaSoloLasSuyas() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        idCreado(token);
        crear(token, """
                {"titulo": "Apartamento en el centro"}
                """).andExpect(status().isCreated());
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));
        crear(iniciarSesion("marta@correo.com"), """
                {"titulo": "Casa ajena"}
                """).andExpect(status().isCreated());

        mvc.perform(get(RUTA + "/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].titulo").value("Apartamento en el centro"))
                .andExpect(jsonPath("$[1].titulo").value("Casa en la playa"));
    }

    @Test
    @DisplayName("Un huésped no tiene publicaciones que listar: responde 403")
    void huespedNoListaPublicaciones() throws Exception {
        mvc.perform(get(RUTA + "/mias").header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("ana@correo.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Otro anfitrión intenta editar la publicación: responde 403 y la publicación no cambia")
    void otroAnfitrionRecibe403() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        editar(iniciarSesion("marta@correo.com"), id, """
                {"titulo": "Casa ajena"}
                """).andExpect(status().isForbidden());

        Publicacion guardada = publicaciones.findById(id).orElseThrow();
        assertThat(guardada.getTitulo()).isEqualTo("Casa en la playa");
        assertThat(guardada.getEditadoPor()).isNull();
    }

    @Test
    @DisplayName("El administrador edita la publicación de un anfitrión: responde 200 y queda como editor")
    void administradorEditaCualquierPublicacion() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        Usuario administrador = usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com",
                codificador.encode(CONTRASENA), Rol.ADMINISTRADOR));

        editar(iniciarSesion("admin@correo.com"), id, """
                {"titulo": "Título corregido"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título corregido"))
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()))
                .andExpect(jsonPath("$.editadoPor").value(administrador.getId().intValue()));
    }

    @Test
    @DisplayName("Un huésped no puede editar publicaciones: responde 403 y la publicación no cambia")
    void huespedNoPuedeEditar() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));

        editar(iniciarSesion("ana@correo.com"), id, """
                {"titulo": "Casa ajena"}
                """).andExpect(status().isForbidden());

        assertThat(publicaciones.findById(id).orElseThrow().getTitulo()).isEqualTo("Casa en la playa");
    }

    @Test
    @DisplayName("Editar una publicación que no existe responde 404")
    void publicacionInexistenteRecibe404() throws Exception {
        editar(iniciarSesion("luis@correo.com"), 999_999L, """
                {"titulo": "Casa renovada"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No encontramos esa publicación."));
    }

    @Test
    @DisplayName("Editar sin título responde 400 y la publicación no cambia")
    void editarSinTituloRecibe400() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idCreado(token);

        editar(token, id, """
                {"titulo": " "}
                """).andExpect(status().isBadRequest());

        assertThat(publicaciones.findById(id).orElseThrow().getTitulo()).isEqualTo("Casa en la playa");
    }

    @Test
    @DisplayName("El dueño consulta su publicación: responde 200 con los datos guardados")
    void duenoConsultaSuPublicacion() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = idCreado(token);

        consultar(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.intValue()))
                .andExpect(jsonPath("$.titulo").value("Casa en la playa"))
                .andExpect(jsonPath("$.capacidad").value(4));
    }

    @Test
    @DisplayName("Otro anfitrión o un huésped no pueden consultar un borrador ajeno: responden 403")
    void consultarBorradorAjenoRecibe403() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        consultar(iniciarSesion("marta@correo.com"), id).andExpect(status().isForbidden());
        consultar(iniciarSesion("ana@correo.com"), id).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El administrador consulta la publicación de un anfitrión: responde 200")
    void administradorConsultaCualquierPublicacion() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));

        consultar(iniciarSesion("admin@correo.com"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anfitrionId").value(anfitrion.getId().intValue()));
    }

    @Test
    @DisplayName("Consultar una publicación que no existe responde 404")
    void consultarInexistenteRecibe404() throws Exception {
        consultar(iniciarSesion("luis@correo.com"), 999_999L).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El dueño elimina su borrador con imágenes y servicios: responde 204 y ya no existe")
    void duenoEliminaSuBorrador() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        String creada = crear(token, """
                {"titulo": "Casa en la playa", "servicios": ["Wifi"],
                 "imagenes": ["https://imagenes.com/1.jpg"]}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(creada).get("id").asLong();

        eliminar(token, id).andExpect(status().isNoContent());

        assertThat(publicaciones.findById(id)).isEmpty();
        consultar(token, id).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Otro anfitrión no puede eliminar la publicación: responde 403 y sigue existiendo")
    void otroAnfitrionNoPuedeEliminar() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA),
                Rol.ANFITRION));

        eliminar(iniciarSesion("marta@correo.com"), id).andExpect(status().isForbidden());

        assertThat(publicaciones.findById(id)).isPresent();
    }

    @Test
    @DisplayName("El administrador elimina el borrador de un anfitrión: responde 204")
    void administradorEliminaCualquierBorrador() throws Exception {
        Long id = idCreado(iniciarSesion("luis@correo.com"));
        usuarios.save(Usuario.nuevo("Admin LR", "admin@correo.com", codificador.encode(CONTRASENA),
                Rol.ADMINISTRADOR));

        eliminar(iniciarSesion("admin@correo.com"), id).andExpect(status().isNoContent());

        assertThat(publicaciones.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Eliminar una publicación que no existe responde 404")
    void eliminarInexistenteRecibe404() throws Exception {
        eliminar(iniciarSesion("luis@correo.com"), 999_999L).andExpect(status().isNotFound());
    }

    private ResultActions consultar(String token, Long id) throws Exception {
        return mvc.perform(get(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions eliminar(String token, Long id) throws Exception {
        return mvc.perform(delete(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long idCreado(String token) throws Exception {
        String respuesta = crear(token, """
                {"titulo": "Casa en la playa", "tipo": "CASA", "capacidad": 4}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private ResultActions editar(String token, Long id, String cuerpo) throws Exception {
        return mvc.perform(put(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
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
