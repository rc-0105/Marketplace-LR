package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.auditoria.infraestructura.CapturaAuditoria;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.SesionInvalidadaRepository;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cableado HTTP y de seguridad de la baja de la cuenta; las reglas se prueban en los unitarios de servicio y entidad. */
@SpringBootTest
@AutoConfigureMockMvc
class BajaCuentaIntegracionTest {

    private static final String RUTA = "/api/v1/perfil/desactivar";
    private static final String RUTA_PROTEGIDA = "/api/v1/otra";
    private static final String CORREO = "ana@correo.com";
    private static final String CONTRASENA = "Segura#2026";
    private static final String INTENTO = "Otra#Clave1";
    private static final String ACTIVA = "ACTIVA";
    private static final String BEARER = "Bearer ";
    private static final String CODIGO = "$.codigo";

    @MockBean
    private ConsultaReservasFuturas reservas;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private SesionInvalidadaRepository sesiones;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        crear(CORREO, Rol.HUESPED);
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    @DisplayName("Cualquier rol con la cuenta activa desactiva la suya con 204 y su token deja de valer")
    void desactivaLaCuentaDeCualquierRol(Rol rol) throws Exception {
        usuarios.deleteAll();
        crear(CORREO, rol);
        String token = iniciarSesion(CORREO);
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, BEARER + token))
                .andExpect(status().isForbidden());

        desactivar(token, CONTRASENA).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(estado(CORREO)).isEqualTo("DESACTIVADA");
        // El estado decide: el token muere sin tocar la denylist.
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, BEARER + token))
                .andExpect(status().isUnauthorized());
        assertThat(sesiones.count()).isZero();
    }

    @Test
    @DisplayName("Tras desactivar, el login da 403 CUENTA_DESACTIVADA, reactivar funciona y la auditoría no lleva datos")
    void luegoElLoginDa403YSePuedeReactivar() throws Exception {
        String token = iniciarSesion(CORREO);

        try (var captura = new CapturaAuditoria()) {
            desactivar(token, CONTRASENA).andExpect(status().isNoContent());

            assertThat(captura.cantidad()).isEqualTo(1);
            assertThat(captura.texto(0)).contains("AUTENTICACION", "desactivar_cuenta", "EXITO")
                    .doesNotContain(CONTRASENA, token, CORREO);
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales()))
                .andExpect(status().isForbidden()).andExpect(jsonPath(CODIGO).value("CUENTA_DESACTIVADA"));
        mvc.perform(post("/api/v1/perfil/reactivar").contentType(MediaType.APPLICATION_JSON).content(credenciales()))
                .andExpect(status().isOk());
        assertThat(estado(CORREO)).isEqualTo(ACTIVA);
    }

    @Test
    @DisplayName("Una contraseña incorrecta da 403 CONFIRMACION_INVALIDA, no cierra la sesión y no cambia la cuenta")
    void contrasenaIncorrectaConservaLaSesion() throws Exception {
        String token = iniciarSesion(CORREO);

        try (var captura = new CapturaAuditoria()) {
            desactivar(token, INTENTO)
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath(CODIGO).value("CONFIRMACION_INVALIDA"))
                    .andExpect(jsonPath("$.detail").value("La contraseña no es correcta."));

            assertThat(captura.texto(0)).contains("credenciales_invalidas", "FALLO")
                    .doesNotContain(INTENTO, CONTRASENA, token);
        }
        assertThat(estado(CORREO)).isEqualTo(ACTIVA);
        assertThat(sesiones.count()).isZero();
        mvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, BEARER + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reservas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"contrasena\": \"\"}", "{\"contrasena\": \"   \"}", "{}", ""})
    @DisplayName("Una contraseña vacía, en blanco o ausente da 400 y no se consulta el puerto de reservas")
    void rechazaCuerpoInvalido(String cuerpo) throws Exception {
        String token = iniciarSesion(CORREO);

        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, BEARER + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        assertThat(estado(CORREO)).isEqualTo(ACTIVA);
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Con reservas pendientes da 409 RESERVAS_FUTURAS_ACTIVAS con el conteo y la cuenta sigue activa")
    void reservasPendientesDan409() throws Exception {
        String token = iniciarSesion(CORREO);
        Long id = usuarios.findAll().get(0).getId();
        when(reservas.contarConfirmadasFuturas(id)).thenReturn(2L);

        desactivar(token, CONTRASENA)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath(CODIGO).value("RESERVAS_FUTURAS_ACTIVAS"))
                .andExpect(jsonPath("$.reservasBloqueantes").value(2))
                .andExpect(jsonPath("$.detail").value(containsString("(2)")));

        assertThat(estado(CORREO)).isEqualTo(ACTIVA);
    }

    @Test
    @DisplayName("Sin token da 401 ProblemDetail y la cuenta no cambia")
    void sinTokenDa401() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo(CONTRASENA)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(estado(CORREO)).isEqualTo(ACTIVA);
    }

    private void crear(String correo, Rol rol) {
        usuarios.save(Usuario.nuevo("Ana Gómez", correo, codificador.encode(CONTRASENA), rol));
    }

    private String iniciarSesion(String correo) throws Exception {
        String cuerpoLogin = "{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, CONTRASENA);
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private ResultActions desactivar(String token, String contrasena) throws Exception {
        return mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(contrasena)));
    }

    private static String cuerpo(String contrasena) {
        return "{\"contrasena\": \"%s\"}".formatted(contrasena);
    }

    private static String credenciales() {
        return "{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(CORREO, CONTRASENA);
    }

    private String estado(String correo) {
        return jdbc.queryForObject("SELECT estado FROM usuario WHERE correo = ?", String.class, correo);
    }
}
