package co.edu.unbosque.marketplace.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** La aplicación sirve el frontend sin abrir nada de la API (SOF-97). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "aplicacion.version=abc1234")
class RutasFrontendIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("La raíz y las rutas de React, con o sin sesión, devuelven index.html")
    void rutasDelFrontendDevuelvenIndex() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/inicio-sesion")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/anfitrion/publicaciones/3/editar")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Un archivo que no existe responde 404 en vez de index.html")
    void archivoInexistenteNoDevuelveIndex() throws Exception {
        mvc.perform(get("/assets/index-inexistente.js")).andExpect(status().isNotFound());
        mvc.perform(get("/favicon-inexistente.svg")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Abrir el frontend no abre la API, el actuator ni otros métodos: siguen negados")
    void laApiYElActuatorSiguenProtegidos() throws Exception {
        mvc.perform(get("/api/v1/publicaciones/mias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ruta-inexistente")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(post("/anfitrion/publicaciones")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El estado público informa el commit desplegado")
    void estadoInformaLaVersion() throws Exception {
        mvc.perform(get("/api/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("abc1234"));
    }
}
