package co.edu.unbosque.marketplace.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.compartido.seguridad.Propiedad;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-04 con la cadena real: el rol del token decide, y lo no declarado se niega (ADR-5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AutorizacionPorRolIntegracionTest.ControladorPrueba.class)
class AutorizacionPorRolIntegracionTest {

    private static final String RUTA_ADMINISTRACION = "/api/v1/prueba-roles/administracion";
    private static final String RUTA_SIN_DECLARAR = "/api/v1/prueba-roles/sin-declarar";
    private static final String RUTA_PUBLICACIONES = "/api/v1/prueba-roles/publicaciones/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder codificador;

    @Autowired
    private PropiedadesJwt propiedades;

    @Autowired
    private UsuarioRepository usuarios;

    private Long idAdministrador;
    private Long idHuesped;
    private Long idAnfitrion;
    private Long idOtroAnfitrion;

    // El validador consulta la cuenta del sub: cada token se firma con un usuario que sí existe.
    @BeforeEach
    void guardarUsuarios() {
        usuarios.deleteAll();
        idAdministrador = guardar("Admin", "admin@correo.com", Rol.ADMINISTRADOR);
        idHuesped = guardar("Hugo", "hugo@correo.com", Rol.HUESPED);
        idAnfitrion = guardar("Ana", "ana@correo.com", Rol.ANFITRION);
        idOtroAnfitrion = guardar("Beto", "beto@correo.com", Rol.ANFITRION);
    }

    @AfterEach
    void limpiar() {
        usuarios.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Un rol distinto de administrador que invoca una operación de administrador recibe 403")
    void otroRolEnOperacionDeAdministradorResponde403(String rol) throws Exception {
        mvc.perform(get(RUTA_ADMINISTRACION).header(HttpHeaders.AUTHORIZATION, bearer(rol, Duration.ofMinutes(5))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para realizar esta acción."));
    }

    @Test
    @DisplayName("El administrador sí ejecuta la operación de administrador")
    void administradorEjecutaLaOperacion() throws Exception {
        mvc.perform(get(RUTA_ADMINISTRACION)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRADOR", Duration.ofMinutes(5))))
                .andExpect(status().isOk())
                .andExpect(content().string("hecho"));
    }

    @ParameterizedTest
    @ValueSource(strings = {RUTA_ADMINISTRACION, RUTA_SIN_DECLARAR})
    @DisplayName("Una ruta protegida sin token responde 401 ProblemDetail")
    void sinTokenResponde401(String ruta) throws Exception {
        mvc.perform(get(ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar."));
    }

    @Test
    @DisplayName("Un token vencido en una operación declarada responde 401")
    void tokenVencidoResponde401() throws Exception {
        mvc.perform(get(RUTA_ADMINISTRACION)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRADOR", Duration.ofHours(-1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar."));
    }

    @Test
    @DisplayName("Un endpoint que no declara roles se niega aunque el token sea de administrador")
    void endpointSinDeclararSeNiega() throws Exception {
        mvc.perform(get(RUTA_SIN_DECLARAR)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRADOR", Duration.ofMinutes(5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permiso para realizar esta acción."));
    }

    @Test
    @DisplayName("Un anfitrión que edita la publicación de otro anfitrión recibe 403")
    void anfitrionEditandoPublicacionAjenaResponde403() throws Exception {
        mvc.perform(put(RUTA_PUBLICACIONES + idOtroAnfitrion)
                        .header(HttpHeaders.AUTHORIZATION, bearer(idAnfitrion, "ANFITRION", Duration.ofMinutes(5))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para realizar esta acción."));
    }

    @Test
    @DisplayName("El anfitrión dueño sí edita su propia publicación")
    void anfitrionEditaSuPublicacion() throws Exception {
        mvc.perform(put(RUTA_PUBLICACIONES + idOtroAnfitrion)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(idOtroAnfitrion, "ANFITRION", Duration.ofMinutes(5))))
                .andExpect(status().isOk())
                .andExpect(content().string("editada"));
    }

    @Test
    @DisplayName("Sin token se responde 401 antes de leer los datos de la petición, aunque sean inválidos")
    void sinTokenConDatosInvalidosResponde401() throws Exception {
        mvc.perform(put("/api/v1/prueba-roles/publicaciones/abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar."));
    }

    @Test
    @DisplayName("Con un rol no permitido se responde 403 antes de leer los datos de la petición, aunque sean inválidos")
    void rolNoPermitidoConDatosInvalidosResponde403() throws Exception {
        mvc.perform(put("/api/v1/prueba-roles/publicaciones/abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer("HUESPED", Duration.ofMinutes(5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permiso para realizar esta acción."));
    }

    private Long guardar(String nombre, String correo, Rol rol) {
        return usuarios.save(Usuario.nuevo(nombre, correo, "hash", rol)).getId();
    }

    private String bearer(String rol, Duration vigencia) {
        Long id = switch (rol) {
            case "ADMINISTRADOR" -> idAdministrador;
            case "ANFITRION" -> idAnfitrion;
            default -> idHuesped;
        };
        return bearer(id, rol, vigencia);
    }

    private String bearer(Long usuarioId, String rol, Duration vigencia) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject(String.valueOf(usuarioId))
                .id(UUID.randomUUID().toString())
                .claim("rol", rol)
                .issuedAt(ahora.minus(Duration.ofHours(2)))
                .expiresAt(ahora.plus(vigencia))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    /**
     * Endpoints de prueba: uno declara el rol administrador, otro no declara nada y
     * otro edita una publicación cuyo dueño viene en la ruta.
     */
    @RestController
    @RequestMapping("/api/v1/prueba-roles")
    static class ControladorPrueba {

        @PutMapping("/publicaciones/{propietarioId}")
        @PreAuthorize(Permisos.ANFITRION)
        String editarPublicacion(@PathVariable Long propietarioId, @AuthenticationPrincipal Jwt jwt) {
            Propiedad.exigirPropietario(propietarioId, Long.valueOf(jwt.getSubject()));
            return "editada";
        }

        @GetMapping("/administracion")
        @PreAuthorize(Permisos.ADMINISTRADOR)
        String administracion() {
            return "hecho";
        }

        @GetMapping("/sin-declarar")
        String sinDeclarar() {
            return "no debería verse";
        }
    }
}
