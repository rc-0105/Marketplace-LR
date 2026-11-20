package co.edu.unbosque.marketplace.auditoria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Crea un anfitrión, un huésped y un administrador y emite sus tokens para las pruebas HTTP de auditoría. */
public final class SesionesAuditoria {

    public static final String ANFITRION = "luis@correo.com";
    public static final String HUESPED = "ana@correo.com";
    public static final String ADMINISTRADOR = "admin@correo.com";

    private static final String CONTRASENA = "Segura#2026";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final UsuarioRepository usuarios;
    private final PublicacionRepository publicaciones;
    private final PasswordEncoder codificador;

    public SesionesAuditoria(MockMvc mvc, ObjectMapper json, UsuarioRepository usuarios,
            PublicacionRepository publicaciones, PasswordEncoder codificador) {
        this.mvc = mvc;
        this.json = json;
        this.usuarios = usuarios;
        this.publicaciones = publicaciones;
        this.codificador = codificador;
    }

    /** Deja solo estos tres usuarios en la base. */
    public void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        usuarios.save(Usuario.nuevo("Luis Pardo", ANFITRION, codificador.encode(CONTRASENA), Rol.ANFITRION));
        usuarios.save(Usuario.nuevo("Ana Gómez", HUESPED, codificador.encode(CONTRASENA), Rol.HUESPED));
        usuarios.save(Usuario.nuevo("Admin", ADMINISTRADOR, codificador.encode(CONTRASENA), Rol.ADMINISTRADOR));
    }

    public String token(String correo) throws Exception {
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
