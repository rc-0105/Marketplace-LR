package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cableado HTTP y de seguridad del perfil propio (RF-07, RF-08); las reglas finas están en los unitarios. */
@SpringBootTest
@AutoConfigureMockMvc
class PerfilIntegracionTest {

    private static final String RUTA = "/api/v1/perfil";
    private static final String RUTA_CONTRASENA = "/api/v1/perfil/contrasena";
    private static final String RUTA_LOGIN = "/api/v1/auth/login";
    private static final String CORREO = "ana@correo.com";
    private static final String CONTRASENA = "Segura#2026";
    private static final String NUEVA = "Nueva#Clave7";
    private static final String TELEFONO = "+573001234567";
    private static final String FOTO = "https://fotos.ejemplo.com/ana.jpg";
    private static final String BEARER = "Bearer ";

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

    @AfterEach
    void limpiarPublicaciones() {
        jdbc.update("DELETE FROM publicacion");
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM publicacion");
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Ana Gómez", CORREO, codificador.encode(CONTRASENA), Rol.HUESPED));
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    @DisplayName("Cualquier rol consulta su perfil con 200 y la respuesta nunca trae la contraseña")
    void consultaElPerfilPropio(Rol rol) throws Exception {
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Ana Gómez", CORREO, codificador.encode(CONTRASENA), rol));
        String token = iniciarSesion(CONTRASENA);

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Gómez"))
                .andExpect(jsonPath("$.correo").value(CORREO))
                .andExpect(jsonPath("$.rol").value(rol.name()))
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.fotoUrl").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    @DisplayName("Actualizar nombre, teléfono y foto responde 200, queda en la base y se ve al consultar de nuevo")
    void actualizaLosDatosYSeVenAlRecargar() throws Exception {
        String token = iniciarSesion(CONTRASENA);
        String cuerpo = json.writeValueAsString(Map.of("nombre", "Ana María", "telefono", TELEFONO, "fotoUrl", FOTO));

        actualizar(token, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana María"))
                .andExpect(jsonPath("$.telefono").value(TELEFONO))
                .andExpect(jsonPath("$.fotoUrl").value(FOTO))
                .andExpect(jsonPath("$.correo").value(CORREO));

        assertThat(jdbc.queryForMap("SELECT nombre, telefono, foto_url FROM usuario WHERE correo = ?", CORREO))
                .containsEntry("nombre", "Ana María").containsEntry("telefono", TELEFONO)
                .containsEntry("foto_url", FOTO);
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana María"))
                .andExpect(jsonPath("$.telefono").value(TELEFONO))
                .andExpect(jsonPath("$.fotoUrl").value(FOTO));
    }

    @Test
    @DisplayName("Intentar cambiar el correo responde 400 con el error en el campo y no cambia nada")
    void noPermiteModificarElCorreo() throws Exception {
        String token = iniciarSesion(CONTRASENA);
        String cuerpo = json.writeValueAsString(Map.of("nombre", "Ana María", "correo", "otro@correo.com"));

        actualizar(token, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores.correo").value("El correo no se puede modificar."));

        assertThat(jdbc.queryForMap("SELECT nombre, correo FROM usuario"))
                .containsEntry("nombre", "Ana Gómez").containsEntry("correo", CORREO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"nombre\": \"\"}",
        "{\"nombre\": \"Ana\", \"telefono\": \"abc123\"}",
        "{\"nombre\": \"Ana\", \"telefono\": \"123\"}",
        "{\"nombre\": \"Ana\", \"fotoUrl\": \"javascript:alert(1)\"}",
        "{\"nombre\": \"Ana\", \"fotoUrl\": \"ftp://fotos.com/a.jpg\"}"})
    @DisplayName("Un nombre vacío, un teléfono o una foto con formato inválido dan 400 y no cambian nada")
    void rechazaFormatosInvalidos(String cuerpo) throws Exception {
        String token = iniciarSesion(CONTRASENA);

        actualizar(token, cuerpo).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForMap("SELECT nombre, telefono, foto_url FROM usuario"))
                .containsEntry("nombre", "Ana Gómez").containsEntry("telefono", null).containsEntry("foto_url", null);
    }

    @Test
    @DisplayName("Con la contraseña actual correcta y una nueva válida responde 204 y solo la nueva sirve para entrar")
    void cambiaLaContrasena() throws Exception {
        String token = iniciarSesion(CONTRASENA);

        try (var captura = new CapturaAuditoria()) {
            cambiarContrasena(token, CONTRASENA, NUEVA)
                    .andExpect(status().isNoContent()).andExpect(content().string(""));

            assertThat(captura.texto(0)).contains("AUTENTICACION", "cambiar_contrasena", "EXITO")
                    .doesNotContain(CONTRASENA, NUEVA, token, CORREO);
        }
        mvc.perform(post(RUTA_LOGIN).contentType(MediaType.APPLICATION_JSON).content(credenciales(CONTRASENA)))
                .andExpect(status().isUnauthorized());
        iniciarSesion(NUEVA);
    }

    @Test
    @DisplayName("Con la contraseña actual incorrecta responde 403 CONFIRMACION_INVALIDA y la contraseña no cambia")
    void rechazaContrasenaActualIncorrecta() throws Exception {
        String token = iniciarSesion(CONTRASENA);
        String hashAntes = hash();

        cambiarContrasena(token, "Otra#Clave1", NUEVA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La contraseña no es correcta."));

        assertThat(hash()).isEqualTo(hashAntes);
        iniciarSesion(CONTRASENA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cor#1a", "sinmayuscula#1", "SinNumero#", "SinEspecial1", ""})
    @DisplayName("Una contraseña nueva que no cumple la regla da 400 y la contraseña no cambia")
    void rechazaContrasenaNuevaInvalida(String nueva) throws Exception {
        String token = iniciarSesion(CONTRASENA);
        String hashAntes = hash();

        cambiarContrasena(token, CONTRASENA, nueva)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasenaNueva").exists());

        assertThat(hash()).isEqualTo(hashAntes);
    }

    @Test
    @DisplayName("Sin token, consultar, actualizar y cambiar la contraseña dan 401 y nada cambia")
    void sinTokenDa401() throws Exception {
        String hashAntes = hash();

        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Intruso\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(RUTA_CONTRASENA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoContrasena(CONTRASENA, NUEVA)))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT nombre FROM usuario", String.class)).isEqualTo("Ana Gómez");
        assertThat(hash()).isEqualTo(hashAntes);
    }

    private String iniciarSesion(String contrasena) throws Exception {
        String respuesta = mvc.perform(post(RUTA_LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(contrasena)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private ResultActions actualizar(String token, String cuerpo) throws Exception {
        return mvc.perform(put(RUTA).header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions cambiarContrasena(String token, String actual, String nueva) throws Exception {
        return mvc.perform(put(RUTA_CONTRASENA).header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoContrasena(actual, nueva)));
    }

    private String hash() {
        return jdbc.queryForObject("SELECT contrasena_hash FROM usuario WHERE correo = ?", String.class, CORREO);
    }

    private static String cuerpoContrasena(String actual, String nueva) {
        return "{\"contrasenaActual\": \"%s\", \"contrasenaNueva\": \"%s\"}".formatted(actual, nueva);
    }

    private static String credenciales(String contrasena) {
        return "{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(CORREO, contrasena);
    }
}
