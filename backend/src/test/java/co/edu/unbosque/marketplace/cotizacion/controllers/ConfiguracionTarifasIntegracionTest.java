package co.edu.unbosque.marketplace.cotizacion.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.cotizacion.model.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.repository.ConfiguracionCotizacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tarifas de la plataforma (SOF-32): token → autorización → servicio → PostgreSQL, y su efecto en la cotización. */
@SpringBootTest
@AutoConfigureMockMvc
class ConfiguracionTarifasIntegracionTest {

    private static final String RUTA = "/api/v1/admin/configuracion/tarifas";
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
    private ConfiguracionCotizacionRepository configuraciones;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Admin", "admin@correo.com", codificador.encode(CONTRASENA), Rol.ADMINISTRADOR));
        usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    // La fila de configuración es compartida con los tests de cotización: se deja como la creó V17.
    @AfterEach
    void limpiar() {
        configuraciones.save(new ConfiguracionCotizacion(new BigDecimal("10"), BigDecimal.ZERO));
        publicaciones.deleteAll();
    }

    @Test
    @DisplayName("El administrador consulta los porcentajes por defecto de V17")
    void consultaLosPorcentajesPorDefecto() throws Exception {
        consultar(iniciarSesion("admin@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentajeServicio").value(10.00))
                .andExpect(jsonPath("$.porcentajeDescuento").value(0.00));
    }

    @Test
    @DisplayName("Al actualizar, la respuesta y la consulta siguiente traen los nuevos porcentajes")
    void actualizaYSePersiste() throws Exception {
        String admin = iniciarSesion("admin@correo.com");

        actualizar(admin, "15.5", "5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentajeServicio").value(15.50))
                .andExpect(jsonPath("$.porcentajeDescuento").value(5.00));

        consultar(admin)
                .andExpect(jsonPath("$.porcentajeServicio").value(15.50))
                .andExpect(jsonPath("$.porcentajeDescuento").value(5.00));
    }

    @Test
    @DisplayName("La cotización siguiente usa de inmediato la nueva tarifa y el nuevo descuento")
    void laCotizacionUsaLosNuevosPorcentajes() throws Exception {
        Long id = publicacionActiva();
        String huesped = iniciarSesion("ana@correo.com");
        cotizar(huesped, id).andExpect(jsonPath("$.tarifaServicio").value(40000.00));

        actualizar(iniciarSesion("admin@correo.com"), "20", "5").andExpect(status().isOk());

        cotizar(huesped, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarifaServicio").value(80000.00))
                .andExpect(jsonPath("$.descuento").value(20000.00))
                .andExpect(jsonPath("$.total").value(490000.00));
    }

    @Test
    @DisplayName("Un porcentaje negativo, mayor a 100, con más de 2 decimales o ausente responde 400 y no cambia nada")
    void rechazaPorcentajesInvalidos() throws Exception {
        String admin = iniciarSesion("admin@correo.com");

        actualizar(admin, "-1", "0").andExpect(status().isBadRequest());
        actualizar(admin, "10", "-0.01").andExpect(status().isBadRequest());
        actualizar(admin, "100.01", "0").andExpect(status().isBadRequest());
        actualizar(admin, "10", "101").andExpect(status().isBadRequest());
        actualizar(admin, "10.555", "0").andExpect(status().isBadRequest());
        mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"porcentajeServicio\": 10}"))
                .andExpect(status().isBadRequest());

        consultar(admin)
                .andExpect(jsonPath("$.porcentajeServicio").value(10.00))
                .andExpect(jsonPath("$.porcentajeDescuento").value(0.00));
    }

    @Test
    @DisplayName("Los límites 0 y 100 son válidos")
    void aceptaLosLimites() throws Exception {
        String admin = iniciarSesion("admin@correo.com");

        actualizar(admin, "0", "100").andExpect(status().isOk());
        actualizar(admin, "100", "0").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Quien no es administrador recibe 403 al consultar y al cambiar, y sin token 401")
    void soloElAdministrador() throws Exception {
        for (String correo : new String[] { "ana@correo.com", "luis@correo.com" }) {
            String token = iniciarSesion(correo);
            consultar(token).andExpect(status().isForbidden());
            actualizar(token, "50", "50").andExpect(status().isForbidden());
        }
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo("50", "50")))
                .andExpect(status().isUnauthorized());

        consultar(iniciarSesion("admin@correo.com")).andExpect(jsonPath("$.porcentajeServicio").value(10.00));
    }

    private ResultActions consultar(String token) throws Exception {
        return mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions actualizar(String token, String servicio, String descuento) throws Exception {
        return mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(servicio, descuento)));
    }

    private String cuerpo(String servicio, String descuento) {
        return """
                {"porcentajeServicio": %s, "porcentajeDescuento": %s}
                """.formatted(servicio, descuento);
    }

    private ResultActions cotizar(String token, Long id) throws Exception {
        return mvc.perform(post("/api/v1/cotizaciones").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"publicacionId": %d, "llegada": "%s", "salida": "%s", "serviciosAdicionales": []}
                        """.formatted(id, LLEGADA, SALIDA)));
    }

    // Crea la publicación por la API y fija estado, precio y limpieza directo: ACTIVA solo se
    // alcanza con la aprobación del administrador, que no es lo que se prueba aquí.
    private Long publicacionActiva() throws Exception {
        Long id = crearBorrador();
        Publicacion publicacion = publicaciones.findById(id).get();
        ReflectionTestUtils.setField(publicacion, "estado", EstadoPublicacion.ACTIVA);
        ReflectionTestUtils.setField(publicacion, "precioNoche", new BigDecimal("100000"));
        ReflectionTestUtils.setField(publicacion, "tarifaLimpieza", new BigDecimal("30000"));
        publicaciones.save(publicacion);
        return id;
    }

    private Long crearBorrador() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/publicaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + iniciarSesion("luis@correo.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Casa en la playa"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
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
