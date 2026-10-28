package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.auditoria.infraestructura.CapturaAuditoria;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Restringir y levantar la restricción (RF-06) con tokens reales y PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class RestriccionCuentaIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String MOTIVO = "Pagos en disputa";
    private static final String CODIGO = "OPERACION_CUENTA_NO_PERMITIDA";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private Long idAdmin;
    private Long idAna;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        idAdmin = guardar("Admin", "admin@correo.com", Rol.ADMINISTRADOR);
        idAna = guardar("Ana Gómez", "ana@correo.com", Rol.HUESPED);
    }

    @Test
    @DisplayName("El administrador restringe una cuenta activa: 204, estado y motivo guardados y auditoría sin el texto")
    void restringeCuentaActiva() throws Exception {
        String token = token("admin@correo.com");

        try (var captura = new CapturaAuditoria()) {
            restringir(token, idAna, cuerpo(MOTIVO)).andExpect(status().isNoContent());

            assertThat(estado(idAna)).isEqualTo("RESTRINGIDA");
            assertThat(motivo(idAna)).isEqualTo(MOTIVO);
            assertThat(captura.cantidad()).isEqualTo(1);
            assertThat(captura.texto(0)).contains("ACCION_ADMINISTRATIVA", "restringir_cuenta", "EXITO")
                    .doesNotContain(MOTIVO, token);
            // El actor sale del sub del token del administrador, no de la ruta ni del cuerpo.
            assertThat(captura.json(0).get("usuario").asText()).isEqualTo(String.valueOf(idAdmin));
            assertThat(captura.json(0).get("entidadId").asText()).isEqualTo(String.valueOf(idAna));
        }
    }

    @Test
    @DisplayName("Un motivo vacío, en blanco, ausente o de más de 500 caracteres da 400 y la cuenta no cambia")
    void rechazaMotivoInvalido() throws Exception {
        String token = token("admin@correo.com");

        for (String cuerpo : new String[] {cuerpo(""), cuerpo("   "), "{}", cuerpo("x".repeat(501))}) {
            restringir(token, idAna, cuerpo).andExpect(status().isBadRequest());
        }

        assertThat(estado(idAna)).isEqualTo("ACTIVA");
        restringir(token, idAna, cuerpo("x".repeat(500))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Restringir una cuenta ya restringida da 409 ProblemDetail con el código y sin cambios")
    void noRestringeCuentaQueNoEstaActiva() throws Exception {
        String token = token("admin@correo.com");
        restringir(token, idAna, cuerpo(MOTIVO)).andExpect(status().isNoContent());

        restringir(token, idAna, cuerpo("Otro motivo")).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value(CODIGO));
        assertThat(motivo(idAna)).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("Un usuario inexistente da 404 ProblemDetail")
    void usuarioInexistente() throws Exception {
        String token = token("admin@correo.com");

        restringir(token, 999_999L, cuerpo(MOTIVO)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No encontramos ese usuario."));
    }

    @Test
    @DisplayName("Levantar la restricción deja la cuenta activa y sin motivo, con auditoría sin el texto")
    void levantaRestriccion() throws Exception {
        String token = token("admin@correo.com");
        restringir(token, idAna, cuerpo(MOTIVO)).andExpect(status().isNoContent());

        try (var captura = new CapturaAuditoria()) {
            levantar(token, idAna).andExpect(status().isNoContent());

            assertThat(captura.texto(0)).contains("ACCION_ADMINISTRATIVA", "levantar_restriccion")
                    .doesNotContain(MOTIVO);
        }

        assertThat(estado(idAna)).isEqualTo("ACTIVA");
        assertThat(motivo(idAna)).isNull();
    }

    @Test
    @DisplayName("Un huésped o un anfitrión reciben 403 antes de leer el cuerpo y la cuenta no cambia")
    void rolesSinPermisoRecibenForbidden() throws Exception {
        guardar("Beto", "beto@correo.com", Rol.ANFITRION);

        for (String correo : new String[] {"ana@correo.com", "beto@correo.com"}) {
            String token = token(correo);
            restringir(token, idAna, "{}").andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            restringir(token, idAna, cuerpo(MOTIVO)).andExpect(status().isForbidden());
            levantar(token, idAna).andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        assertThat(estado(idAna)).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Sin token las dos operaciones dan 401")
    void sinTokenResponde401() throws Exception {
        mvc.perform(put(ruta(idAna)).contentType(MediaType.APPLICATION_JSON).content(cuerpo(MOTIVO)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(ruta(idAna))).andExpect(status().isUnauthorized());
    }

    private Long guardar(String nombre, String correo, Rol rol) {
        return usuarios.save(Usuario.nuevo(nombre, correo, codificador.encode(CONTRASENA), rol)).getId();
    }

    private String estado(Long id) {
        return jdbc.queryForObject("SELECT estado FROM usuario WHERE id = ?", String.class, id);
    }

    private String motivo(Long id) {
        return jdbc.queryForObject("SELECT motivo_restriccion FROM usuario WHERE id = ?", String.class, id);
    }

    private static String ruta(Long id) {
        return "/api/v1/admin/usuarios/" + id + "/restriccion";
    }

    private static String cuerpo(String motivo) {
        return "{\"motivo\": \"" + motivo + "\"}";
    }

    private String token(String correo) throws Exception {
        String respuesta = login(correo).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private ResultActions login(String correo) throws Exception {
        String cuerpo = """
                {"correo": "%s", "contrasena": "%s"}
                """.formatted(correo, CONTRASENA);
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions restringir(String token, Long id, String cuerpo) throws Exception {
        return mvc.perform(put(ruta(id)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions levantar(String token, Long id) throws Exception {
        return mvc.perform(delete(ruta(id)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
