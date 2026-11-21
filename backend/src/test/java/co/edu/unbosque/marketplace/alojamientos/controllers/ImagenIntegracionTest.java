package co.edu.unbosque.marketplace.alojamientos.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Subida y lectura de imágenes (RF-19): token → autorización → servicio → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class ImagenIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6};

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

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Marta Ruiz", "marta@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
    }

    // Las imágenes caen en cascada con su publicación, y las publicaciones referencian al usuario.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("El dueño sube un PNG, recibe su URL y cualquier visitante la lee sin sesión")
    void elDuenoSubeYElVisitanteLee() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearPublicacion(token);

        String respuesta = subir(token, id, "foto.png", PNG).andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").exists()).andReturn().getResponse().getContentAsString();
        String url = json.readTree(respuesta).get("url").asText();

        assertThat(url).startsWith("/api/v1/imagenes/");
        byte[] leida = mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG)).andReturn().getResponse().getContentAsByteArray();
        assertThat(leida).isEqualTo(PNG);
    }

    @Test
    @DisplayName("La URL subida se puede guardar como imagen de la publicación")
    void laUrlSubidaSePuedeGuardar() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearPublicacion(token);
        String url = json.readTree(subir(token, id, "foto.png", PNG).andReturn().getResponse().getContentAsString())
                .get("url").asText();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/publicaciones/" + id + "/imagenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imagenes\": [\"" + url + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagenes[0]").value(url));
    }

    @Test
    @DisplayName("Otro anfitrión no puede subir a una publicación ajena")
    void otroAnfitrionNoSube() throws Exception {
        Long id = crearPublicacion(iniciarSesion("luis@correo.com"));

        subir(iniciarSesion("marta@correo.com"), id, "foto.png", PNG).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin sesión no se sube nada")
    void sinSesionNoSube() throws Exception {
        Long id = crearPublicacion(iniciarSesion("luis@correo.com"));

        mvc.perform(multipart("/api/v1/publicaciones/" + id + "/imagenes/archivo")
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un archivo que no es PNG ni JPG se rechaza aunque diga ser image/png")
    void rechazaUnArchivoFalso() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearPublicacion(token);

        subir(token, id, "foto.png", "<script>alert(1)</script>".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Solo se admiten imágenes PNG o JPG."));
    }

    @Test
    @DisplayName("Sin el campo archivo responde 400")
    void sinArchivo() throws Exception {
        String token = iniciarSesion("luis@correo.com");
        Long id = crearPublicacion(token);

        mvc.perform(multipart("/api/v1/publicaciones/" + id + "/imagenes/archivo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una imagen inexistente responde 404 y la lectura no abre otras rutas de la API")
    void imagenInexistenteYRutasProtegidas() throws Exception {
        mvc.perform(get("/api/v1/imagenes/0b9d7c1e-4f55-4c2e-9a3b-6d1f2a8e5c70")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/publicaciones/mias")).andExpect(status().isUnauthorized());
    }

    private ResultActions subir(String token, Long id, String nombre, byte[] contenido) throws Exception {
        return mvc.perform(multipart("/api/v1/publicaciones/" + id + "/imagenes/archivo")
                .file(new MockMultipartFile("archivo", nombre, "image/png", contenido))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long crearPublicacion(String token) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": \"Casa en la playa\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private String iniciarSesion(String correo) throws Exception {
        String cuerpo = "{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
