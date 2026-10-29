package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import co.edu.unbosque.marketplace.identidad.exceptions.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaRestringidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaYaActivaException;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inicio de sesión (RF-02): respuesta genérica ante cualquier credencial inválida. */
@Service
public class AutenticacionService {

    private static final String ENTIDAD = "Usuario";
    private static final String OPERACION = "operacion";
    private static final String MOTIVO = "motivo";
    private static final String LOGIN = "login";
    private static final String REACTIVAR = "reactivar_cuenta";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final EmisorTokens emisor;
    private final PublicadorEventos publicador;
    private final SesionService sesiones;
    // Con un correo desconocido se compara contra este hash para igualar el costo de BCrypt.
    private final String hashFicticio;

    public AutenticacionService(UsuarioRepository usuarios, PasswordEncoder codificador, EmisorTokens emisor,
            PublicadorEventos publicador, SesionService sesiones) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emisor = emisor;
        this.publicador = publicador;
        this.sesiones = sesiones;
        this.hashFicticio = codificador.encode(UUID.randomUUID().toString());
    }

    public SesionIniciada iniciarSesion(String correo, String contrasena) {
        Usuario usuario = verificarCredenciales(correo, contrasena, LOGIN);
        if (!usuario.puedeIniciarSesion()) {
            throw rechazarPorEstado(usuario, LOGIN);
        }

        TokenEmitido token = emisor.emitir(usuario);
        publicador.publicar(new EventoCritico(String.valueOf(usuario.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(usuario.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of(OPERACION, LOGIN)));
        return new SesionIniciada(usuario, token);
    }

    // El estado se revisa solo después de la contraseña, para no revelar a quien no la conoce.
    private Usuario verificarCredenciales(String correo, String contrasena, String operacion) {
        Optional<Usuario> encontrado = usuarios.findByCorreo(correo.trim().toLowerCase(Locale.ROOT));
        boolean coincide = codificador.matches(contrasena,
                encontrado.map(Usuario::getContrasenaHash).orElse(hashFicticio));

        if (encontrado.isEmpty() || !coincide) {
            publicador.publicar(new EventoCritico(null, AccionCritica.AUTENTICACION, ENTIDAD, null,
                    ResultadoEvento.FALLO, Instant.now(),
                    Map.of(OPERACION, operacion, MOTIVO, "credenciales_invalidas")));
            throw new CredencialesInvalidasException();
        }
        return encontrado.get();
    }

    // Exhaustivo a propósito: un estado nuevo no compila hasta que se decida qué responderle.
    private NegocioException rechazarPorEstado(Usuario usuario, String operacion) {
        return switch (usuario.getEstado()) {
            case RESTRINGIDA -> {
                auditarFallo(usuario, operacion, "cuenta_restringida");
                yield new CuentaRestringidaException(usuario.getMotivoRestriccion());
            }
            case DESACTIVADA -> {
                auditarFallo(usuario, operacion, "cuenta_desactivada");
                yield new CuentaDesactivadaException();
            }
            // Al cliente le responde igual que un correo inexistente; solo la auditoría sabe quién fue.
            case ELIMINADA -> {
                auditarFallo(usuario, operacion, "cuenta_eliminada");
                yield new CredencialesInvalidasException();
            }
            case ACTIVA -> {
                auditarFallo(usuario, operacion, "cuenta_activa");
                yield new CuentaYaActivaException();
            }
        };
    }

    private void auditarFallo(Usuario usuario, String operacion, String motivo) {
        publicador.publicar(new EventoCritico(String.valueOf(usuario.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(usuario.getId()), ResultadoEvento.FALLO, Instant.now(),
                Map.of(OPERACION, operacion, MOTIVO, motivo)));
    }

    /**
     * Reactiva una cuenta desactivada con las credenciales de su dueño y le inicia sesión (RF-06).
     * Una cuenta restringida no se reactiva: solo un administrador levanta la restricción.
     */
    @Transactional
    public SesionIniciada reactivarCuenta(String correo, String contrasena) {
        Usuario usuario = verificarCredenciales(correo, contrasena, REACTIVAR);
        Usuario reactivada = switch (usuario.getEstado()) {
            case RESTRINGIDA, ACTIVA, ELIMINADA -> throw rechazarPorEstado(usuario, REACTIVAR);
            case DESACTIVADA -> {
                usuario.reactivar();
                usuarios.save(usuario);
                yield usuario;
            }
        };

        TokenEmitido token = emisor.emitir(reactivada);
        publicador.publicar(new EventoCritico(String.valueOf(reactivada.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(reactivada.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of(OPERACION, REACTIVAR)));
        return new SesionIniciada(reactivada, token);
    }

    /** Invalida el token de la sesión (RF-03). Es idempotente: repetirlo no duplica ni falla. */
    public void cerrarSesion(String jti, Long usuarioId, Instant expiraEn) {
        sesiones.invalidar(jti, usuarioId, expiraEn);
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), AccionCritica.AUTENTICACION, ENTIDAD,
                String.valueOf(usuarioId), ResultadoEvento.EXITO, Instant.now(),
                Map.of(OPERACION, "logout")));
    }
}
