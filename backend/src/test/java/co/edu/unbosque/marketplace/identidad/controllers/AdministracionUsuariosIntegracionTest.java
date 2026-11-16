package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Listar, restringir y rehabilitar cuentas (RF-06, RF-58) con tokens reales y PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class AdministracionUsuariosIntegracionTest {

    private static final String CONTRASENA = "Segura#2026";
    private static final String MOTIVO = "Pagos en disputa";
    private static final String RUTA_PROTEGIDA = "/api/v1/otra";
    private static final String CODIGO = "OPERACION_CUENTA_NO_PERMITIDA";
    private static final String RUTA_LISTADO = "/api/v1/admin/usuarios";

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
    @DisplayName("El administrador no puede restringirse a sí mismo: 409 con el mensaje, sigue activo y puede entrar")
    void noPuedeRestringirseASiMismo() throws Exception {
        String token = token("admin@correo.com");

        restringir(token, idAdmin, cuerpo(MOTIVO)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value(CODIGO))
                .andExpect(jsonPath("$.detail").value("No puedes restringir tu propia cuenta."));

        assertThat(estado(idAdmin)).isEqualTo("ACTIVA");
        assertThat(motivo(idAdmin)).isNull();
        login("admin@correo.com").andExpect(status().isOk());
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
    @DisplayName("Rehabilitar deja la cuenta activa y sin motivo, con auditoría sin el texto")
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
            listar(token, "").andExpect(status().isForbidden());
        }

        assertThat(estado(idAna)).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("El listado filtra por nombre o correo sin distinguir mayúsculas, ordena por nombre y omite eliminadas")
    void listaCuentasParaAdministrar() throws Exception {
        String token = token("admin@correo.com");
        Long beto = guardar("Beto Ruiz", "beto@otro.com", Rol.ANFITRION);
        Usuario eliminada = usuarios.findById(guardar("Ana Vieja", "ana.vieja@correo.com", Rol.HUESPED)).orElseThrow();
        eliminada.eliminar(codificador.encode("inservible"));
        usuarios.save(eliminada);
        restringir(token, beto, cuerpo(MOTIVO)).andExpect(status().isNoContent());

        listar(token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Admin"))
                .andExpect(jsonPath("$[1].nombre").value("Ana Gómez"))
                .andExpect(jsonPath("$[2].nombre").value("Beto Ruiz"))
                .andExpect(jsonPath("$[2].estado").value("RESTRINGIDA"))
                .andExpect(jsonPath("$[2].motivoRestriccion").value(MOTIVO))
                .andExpect(jsonPath("$[2].rol").value("ANFITRION"))
                .andExpect(jsonPath("$[0].contrasenaHash").doesNotExist());
        listar(token, "  ANA ").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(idAna));
        listar(token, "OTRO.COM").andExpect(jsonPath("$[0].id").value(beto));
        listar(token, "nadie").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Sin token el listado, restringir y rehabilitar dan 401")
    void sinTokenResponde401() throws Exception {
        mvc.perform(post(ruta(idAna, "restringir")).contentType(MediaType.APPLICATION_JSON).content(cuerpo(MOTIVO)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ruta(idAna, "rehabilitar"))).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA_LISTADO)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Al restringir a Ana su token vivo da 401, su login da 403 con motivo y al rehabilitarla vuelve a entrar")
    void restriccionInvalidaElTokenVivo() throws Exception {
        String admin = token("admin@correo.com");
        String ana = token("ana@correo.com");
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + ana))
                .andExpect(status().isForbidden());

        restringir(admin, idAna, cuerpo(MOTIVO)).andExpect(status().isNoContent());

        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + ana))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        login("ana@correo.com").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"))
                .andExpect(jsonPath("$.motivo").value(MOTIVO));

        levantar(admin, idAna).andExpect(status().isNoContent());

        login("ana@correo.com").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un administrador ya restringido que usa su token recibe 401")
    void administradorRestringidoPierdeSuSesion() throws Exception {
        Long otro = guardar("Otro admin", "otro@correo.com", Rol.ADMINISTRADOR);
        String tokenOtro = token("otro@correo.com");
        String admin = token("admin@correo.com");

        restringir(admin, otro, cuerpo(MOTIVO)).andExpect(status().isNoContent());

        levantar(tokenOtro, idAna).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token vivo de una cuenta desactivada o eliminada recibe 401")
    void cuentaDesactivadaOEliminadaPierdeSuSesion() throws Exception {
        Long beto = guardar("Beto", "beto@correo.com", Rol.HUESPED);
        String tokenAna = token("ana@correo.com");
        String tokenBeto = token("beto@correo.com");

        jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA' WHERE id = ?", idAna);
        jdbc.update("DELETE FROM usuario WHERE id = ?", beto);

        for (String token : new String[] {tokenAna, tokenBeto}) {
            mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
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

    private static String ruta(Long id, String accion) {
        return RUTA_LISTADO + "/" + id + "/" + accion;
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
        return mvc.perform(post(ruta(id, "restringir")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions listar(String token, String texto) throws Exception {
        return mvc.perform(get(RUTA_LISTADO).param("texto", texto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions levantar(String token, Long id) throws Exception {
        return mvc.perform(post(ruta(id, "rehabilitar")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
