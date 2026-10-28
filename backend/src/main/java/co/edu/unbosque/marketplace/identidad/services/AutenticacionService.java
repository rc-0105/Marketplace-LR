package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import co.edu.unbosque.marketplace.identidad.exceptions.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaRestringidaException;
import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Inicio de sesión (RF-02): respuesta genérica ante cualquier credencial inválida. */
@Service
public class AutenticacionService {

    private static final String ENTIDAD = "Usuario";
    private static final String OPERACION = "operacion";
    private static final String MOTIVO = "motivo";

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
        Usuario usuario = verificarCredenciales(correo, contrasena, "login");
        if (!usuario.puedeIniciarSesion()) {
            throw rechazarPorEstado(usuario, "login");
        }

        TokenEmitido token = emisor.emitir(usuario);
        publicador.publicar(new EventoCritico(String.valueOf(usuario.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(usuario.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of(OPERACION, "login")));
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

    private NegocioException rechazarPorEstado(Usuario usuario, String operacion) {
        boolean restringida = usuario.getEstado() == EstadoCuenta.RESTRINGIDA;
        publicador.publicar(new EventoCritico(String.valueOf(usuario.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(usuario.getId()), ResultadoEvento.FALLO, Instant.now(),
                Map.of(OPERACION, operacion, MOTIVO, restringida ? "cuenta_restringida" : "cuenta_desactivada")));
        return restringida
                ? new CuentaRestringidaException(usuario.getMotivoRestriccion())
                : new CuentaDesactivadaException();
    }

    /** Invalida el token de la sesión (RF-03). Es idempotente: repetirlo no duplica ni falla. */
    public void cerrarSesion(String jti, Long usuarioId, Instant expiraEn) {
        sesiones.invalidar(jti, usuarioId, expiraEn);
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), AccionCritica.AUTENTICACION, ENTIDAD,
                String.valueOf(usuarioId), ResultadoEvento.EXITO, Instant.now(),
                Map.of(OPERACION, "logout")));
    }
}
