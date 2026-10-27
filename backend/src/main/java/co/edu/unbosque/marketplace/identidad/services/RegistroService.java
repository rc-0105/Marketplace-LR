package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.CorreoEnUsoException;
import co.edu.unbosque.marketplace.identidad.exceptions.RolNoPermitidoException;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro público de cuentas de huésped y anfitrión (RF-01). */
@Service
public class RegistroService {

    private static final String ENTIDAD = "Usuario";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final PublicadorEventos publicador;

    public RegistroService(UsuarioRepository usuarios, PasswordEncoder codificador, PublicadorEventos publicador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.publicador = publicador;
    }

    @Transactional
    public Usuario registrar(String nombre, String correo, String contrasena, String rol) {
        Rol rolSolicitado = rolDeRegistro(rol);
        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);

        if (usuarios.existsByCorreo(correoNormalizado)) {
            auditarCorreoEnUso();
            throw new CorreoEnUsoException();
        }

        Usuario usuario;
        try {
            usuario = usuarios.saveAndFlush(Usuario.nuevo(
                    nombre.trim(), correoNormalizado, codificador.encode(contrasena), rolSolicitado));
        } catch (DataIntegrityViolationException carrera) {
            // Otro registro con el mismo correo ganó entre la consulta y el insert;
            // el índice único de la tabla es quien tiene la última palabra.
            auditarCorreoEnUso();
            throw new CorreoEnUsoException();
        }

        publicador.publicar(new EventoCritico(String.valueOf(usuario.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(usuario.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of("operacion", "registro", "rol", rolSolicitado.name())));
        return usuario;
    }

    private static Rol rolDeRegistro(String rol) {
        return Arrays.stream(Rol.values())
                .filter(Rol::permiteRegistro)
                .filter(candidato -> candidato.name().equals(rol))
                .findFirst()
                .orElseThrow(RolNoPermitidoException::new);
    }

    private void auditarCorreoEnUso() {
        publicador.publicar(new EventoCritico(null, AccionCritica.AUTENTICACION, ENTIDAD, null,
                ResultadoEvento.FALLO, Instant.now(),
                Map.of("operacion", "registro", "motivo", "correo_en_uso")));
    }
}
