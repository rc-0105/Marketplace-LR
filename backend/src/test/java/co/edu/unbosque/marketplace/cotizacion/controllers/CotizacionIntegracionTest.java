package co.edu.unbosque.marketplace.cotizacion.controllers;

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

/** Cotización (SOF-31): token → autorización → servicio → PostgreSQL, con los porcentajes de V17. */
@SpringBootTest
@AutoConfigureMockMvc
class CotizacionIntegracionTest {

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

    // Los servicios adicionales caen en cascada con la publicación, y las publicaciones referencian
    // al usuario: si quedan, los tests de otros módulos no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("Cotiza cuatro noches con el desglose completo y los porcentajes por defecto")
    void cotizaConElDesglose() throws Exception {
        Long id = publicacion("ACTIVA");

        cotizar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noches").value(4))
                .andExpect(jsonPath("$.precioBase").value(400000.00))
                .andExpect(jsonPath("$.tarifaLimpieza").value(30000.00))
                .andExpect(jsonPath("$.tarifaServicio").value(40000.00))
                .andExpect(jsonPath("$.descuento").value(0.00))
                .andExpect(jsonPath("$.serviciosAdicionales.length()").value(0))
                .andExpect(jsonPath("$.total").value(470000.00));
    }

    @Test
    @DisplayName("Un servicio adicional es una línea aparte y el total sube exactamente su costo")
    void cotizaConUnServicioAdicional() throws Exception {
        Long id = publicacion("ACTIVA");
        Long desayuno = servicioAdicional(id, "Desayuno", 15000);

        cotizar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[" + desayuno + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviciosAdicionales.length()").value(1))
                .andExpect(jsonPath("$.serviciosAdicionales[0].nombre").value("Desayuno"))
                .andExpect(jsonPath("$.serviciosAdicionales[0].costo").value(15000.00))
                .andExpect(jsonPath("$.totalServiciosAdicionales").value(15000.00))
                .andExpect(jsonPath("$.total").value(485000.00));
    }

    @Test
    @DisplayName("Un servicio adicional de otra publicación responde 400")
    void servicioAdicionalAjenoResponde400() throws Exception {
        Long id = publicacion("ACTIVA");
        Long otra = publicacion("ACTIVA");
        Long ajeno = servicioAdicional(otra, "Traslado", 40000);

        cotizar(iniciarSesion("ana@correo.com"), id, LLEGADA, SALIDA, "[" + ajeno + "]")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Si la salida no es posterior a la llegada responde 400")
    void rechazaSalidaNoPosterior() throws Exception {
        Long id = publicacion("ACTIVA");

        cotizar(iniciarSesion("ana@correo.com"), id, SALIDA, LLEGADA, "[]").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una publicación que no está ACTIVA o no existe responde 404")
    void publicacionNoActivaResponde404() throws Exception {
        String huesped = iniciarSesion("ana@correo.com");

        cotizar(huesped, publicacion("BORRADOR"), LLEGADA, SALIDA, "[]").andExpect(status().isNotFound());
        cotizar(huesped, 999999L, LLEGADA, SALIDA, "[]").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Solo el huésped cotiza: el anfitrión recibe 403 y sin token 401")
    void soloElHuespedCotiza() throws Exception {
        Long id = publicacion("ACTIVA");

        cotizar(iniciarSesion("luis@correo.com"), id, LLEGADA, SALIDA, "[]").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content(cuerpo(id,
                LLEGADA, SALIDA, "[]"))).andExpect(status().isUnauthorized());
    }

    private ResultActions cotizar(String token, Long id, LocalDate llegada, LocalDate salida, String adicionales)
            throws Exception {
        return mvc.perform(post("/api/v1/cotizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(id, llegada, salida, adicionales)));
    }

    private String cuerpo(Long id, LocalDate llegada, LocalDate salida, String adicionales) {
        return """
                {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionales": %s}
                """.formatted(id, llegada, salida, adicionales);
    }

    // Crea la publicación por la API y fija estado, precio y limpieza directo: ACTIVA solo se
    // alcanza con la aprobación del administrador, que no es lo que se prueba aquí.
    private Long publicacion(String estado) throws Exception {
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
