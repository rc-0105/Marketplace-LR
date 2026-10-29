package co.edu.unbosque.marketplace.identidad.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre la recuperación completa: solicitud → código en el log → confirmación → login (RF-05). */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RecuperacionIntegracionTest {

    private static final String SOLICITAR = "/api/v1/auth/recuperacion";
    private static final String CONFIRMAR = "/api/v1/auth/recuperacion/confirmar";
    private static final String CORREO = "ana@correo.com";
    private static final String ANTERIOR = "Segura#2026";
    private static final String NUEVA = "Nueva#2027";
    private static final String CODIGO_INVALIDO = "El código no es válido o ya venció. Solicita uno nuevo.";
    private static final Pattern CODIGO_EN_LOG = Pattern.compile("es (\\d{6}) y vence");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Ana Gómez", CORREO, codificador.encode(ANTERIOR), Rol.HUESPED));
    }

    @Test
    @DisplayName("Con un código vigente se define la contraseña nueva y se inicia sesión con ella")
    void restableceContrasenaEIniciaSesion(CapturedOutput salida) throws Exception {
        solicitar(CORREO).andExpect(status().isAccepted());
        String codigo = ultimoCodigo(salida);

        confirmar(CORREO, codigo, NUEVA).andExpect(status().isNoContent());

        login(NUEVA).andExpect(status().isOk());
        login(ANTERIOR).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un código ya usado se rechaza y la contraseña definida con él sigue vigente")
    void rechazaCodigoUsado(CapturedOutput salida) throws Exception {
        solicitar(CORREO);
        String codigo = ultimoCodigo(salida);
        confirmar(CORREO, codigo, NUEVA).andExpect(status().isNoContent());

        confirmar(CORREO, codigo, "Otra#2028")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(CODIGO_INVALIDO));

        login(NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un código vencido se rechaza y la contraseña anterior sigue vigente")
    void rechazaCodigoVencido(CapturedOutput salida) throws Exception {
        solicitar(CORREO);
        String codigo = ultimoCodigo(salida);
        jdbc.update("UPDATE token_recuperacion SET vence_en = now() - interval '1 minute'");

        confirmar(CORREO, codigo, NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(CODIGO_INVALIDO));

        login(ANTERIOR).andExpect(status().isOk());
        login(NUEVA).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Los intentos fallidos quedan guardados aunque la confirmación responda con error")
    void persisteIntentosFallidos(CapturedOutput salida) throws Exception {
        solicitar(CORREO);
        String erroneo = ultimoCodigo(salida).equals("000000") ? "111111" : "000000";

        confirmar(CORREO, erroneo, NUEVA).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM token_recuperacion", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Con un correo no registrado la respuesta es idéntica a la de un correo registrado")
    void respondeIgualSinRevelarCorreos() throws Exception {
        MvcResult registrado = solicitar(CORREO).andReturn();
        MvcResult desconocido = solicitar("nadie@correo.com").andReturn();

        assertThat(desconocido.getResponse().getStatus()).isEqualTo(registrado.getResponse().getStatus())
                .isEqualTo(202);
        assertThat(desconocido.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(registrado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Si el correo está registrado");
    }

    @Test
    @DisplayName("El código nunca viaja en la respuesta HTTP")
    void noDevuelveElCodigo(CapturedOutput salida) throws Exception {
        String cuerpo = solicitar(CORREO).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(cuerpo).doesNotContain(ultimoCodigo(salida));
    }

    @Test
    @DisplayName("La contraseña nueva debe cumplir la misma regla que el registro")
    void validaLaContrasenaNueva(CapturedOutput salida) throws Exception {
        solicitar(CORREO);

        confirmar(CORREO, ultimoCodigo(salida), "debil")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasenaNueva").exists());
        login(ANTERIOR).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un token vencido que el cliente aún guarda no impide pedir el código")
    void ignoraBearerVencido() throws Exception {
        mvc.perform(post(SOLICITAR).header(HttpHeaders.AUTHORIZATION, "Bearer token.vencido.invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("correo", CORREO))))
                .andExpect(status().isAccepted());
    }

    private ResultActions solicitar(String correo) throws Exception {
        return mvc.perform(post(SOLICITAR).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo))));
    }

    private ResultActions confirmar(String correo, String codigo, String contrasenaNueva) throws Exception {
        return mvc.perform(post(CONFIRMAR).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        Map.of("correo", correo, "codigo", codigo, "contrasenaNueva", contrasenaNueva))));
    }

    private ResultActions login(String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", CORREO, "contrasena", contrasena))));
    }

    private static String ultimoCodigo(CapturedOutput salida) {
        Matcher coincidencia = CODIGO_EN_LOG.matcher(salida.getOut());
        String codigo = null;
        while (coincidencia.find()) {
            codigo = coincidencia.group(1);
        }
        assertThat(codigo).as("código en el log").isNotNull();
        return codigo;
    }
}
