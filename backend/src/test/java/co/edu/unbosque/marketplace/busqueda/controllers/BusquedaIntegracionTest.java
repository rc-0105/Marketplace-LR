package co.edu.unbosque.marketplace.busqueda.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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

/** Búsqueda (SOF-28): token → autorización → servicios de tres módulos → PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class BusquedaIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LocalDate.now().plusDays(15);

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

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // Los periodos caen en cascada con la publicación, y las publicaciones referencian
    // al usuario: si quedan, los tests de otros módulos no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Devuelve solo las ACTIVAS de la ciudad con un periodo que cubre la estancia, sin distinguir mayúsculas")
    void devuelveLasActivasConDisponibilidad() throws Exception {
        String anfitrion = iniciarSesion("luis@correo.com");
        Long cubierta = publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        publicacion(anfitrion, "Santa Marta", "ACTIVA", LLEGADA.plusDays(1), SALIDA.plusDays(5));
        publicacion(anfitrion, "Santa Marta", "BORRADOR", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        publicacion(anfitrion, "Santa Marta", "PAUSADA", LLEGADA.minusDays(2), SALIDA.plusDays(2));
        publicacion(anfitrion, "Cartagena", "ACTIVA", LLEGADA.minusDays(2), SALIDA.plusDays(2));

        buscar(iniciarSesion("ana@correo.com"), "santa marta", LLEGADA, SALIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(cubierta.intValue()))
                .andExpect(jsonPath("$[0].ciudad").value("Santa Marta"));
    }

    @Test
    @DisplayName("Si ninguna publicación cumple responde 200 con una lista vacía")
    void sinResultadosRespondeListaVacia() throws Exception {
        buscar(iniciarSesion("ana@correo.com"), "Leticia", LLEGADA, SALIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Si la salida no es posterior a la llegada responde 400 y no busca")
    void rechazaSalidaNoPosterior() throws Exception {
        buscar(iniciarSesion("ana@correo.com"), "Santa Marta", SALIDA, LLEGADA)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Solo el huésped busca: el anfitrión recibe 403 y sin token 401")
    void soloElHuespedBusca() throws Exception {
        buscar(iniciarSesion("luis@correo.com"), "Santa Marta", LLEGADA, SALIDA)
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/busqueda").param("ciudad", "Santa Marta").param("llegada", LLEGADA.toString())
                .param("salida", SALIDA.toString())).andExpect(status().isUnauthorized());
    }

    private ResultActions buscar(String token, String ciudad, LocalDate llegada, LocalDate salida) throws Exception {
        return mvc.perform(get("/api/v1/busqueda").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("ciudad", ciudad).param("llegada", llegada.toString()).param("salida", salida.toString()));
    }

    // Crea la publicación por la API y fija ciudad, estado y periodo directo: ACTIVA solo se
    // alcanza con la aprobación del administrador, que no es lo que se prueba aquí.
    private Long publicacion(String token, String ciudad, String estado, LocalDate inicio, LocalDate fin)
            throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        jdbc.update("UPDATE publicacion SET ciudad = ?, estado = ? WHERE id = ?", ciudad, estado, id);
        jdbc.update("INSERT INTO periodo_disponible (publicacion_id, fecha_inicio, fecha_fin) VALUES (?, ?, ?)", id,
                java.sql.Date.valueOf(inicio), java.sql.Date.valueOf(fin));
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
