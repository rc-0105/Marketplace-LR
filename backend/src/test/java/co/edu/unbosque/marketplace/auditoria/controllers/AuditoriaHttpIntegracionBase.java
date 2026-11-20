package co.edu.unbosque.marketplace.auditoria.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.auditoria.SemillaAuditoria;
import co.edu.unbosque.marketplace.auditoria.SesionesAuditoria;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Base de las pruebas HTTP de auditoría: usuarios, token de administrador y tabla vacía en cada caso. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AuditoriaHttpIntegracionBase {

    protected static final String RUTA = "/api/v1/admin/auditoria";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private PasswordEncoder codificador;

    protected SemillaAuditoria semilla;
    protected SesionesAuditoria sesiones;
    protected String token;

    @BeforeEach
    void prepararBase() throws Exception {
        semilla = new SemillaAuditoria(jdbc);
        sesiones = new SesionesAuditoria(mvc, json, usuarios, publicaciones, codificador);
        sesiones.preparar();
        token = sesiones.token(SesionesAuditoria.ADMINISTRADOR);
        // Iniciar sesión también deja un evento de auditoría: se vacía después de obtener los tokens.
        semilla.vaciar();
    }

    protected ResultActions consultar(String ruta) throws Exception {
        return consultar(ruta, token);
    }

    protected ResultActions consultar(String ruta, String tokenDeSesion) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeSesion));
    }
}
