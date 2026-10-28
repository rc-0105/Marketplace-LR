package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.CodigoRecuperacionInvalidoException;
import co.edu.unbosque.marketplace.identidad.model.TokenRecuperacion;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.TokenRecuperacionRepository;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperación de contraseña con un código de un solo uso (RF-05). Ninguna
 * respuesta revela si el correo está registrado.
 */
@Service
public class RecuperacionService {

    private static final String ENTIDAD = "Usuario";
    private static final String OPERACION = "operacion";
    private static final String SOLICITUD = "solicitud_recuperacion";
    private static final String CONFIRMACION = "confirmacion_recuperacion";
    private static final Duration VIGENCIA = Duration.ofMinutes(15);

    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final PasswordEncoder codificador;
    private final EnvioCodigoRecuperacion envio;
    private final PublicadorEventos publicador;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    public RecuperacionService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
            PasswordEncoder codificador, EnvioCodigoRecuperacion envio, PublicadorEventos publicador, Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codificador = codificador;
        this.envio = envio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    /** Emite un código nuevo y anula los pendientes. Con un correo desconocido no hace nada visible. */
    @Transactional
    public void solicitar(String correo) {
        String correoNormalizado = normalizar(correo);
        Optional<Usuario> encontrado = usuarios.findByCorreo(correoNormalizado)
                .filter(Usuario::puedeIniciarSesion);
        if (encontrado.isEmpty()) {
            auditarFallo(SOLICITUD, "correo_no_registrado");
            return;
        }

        Usuario usuario = encontrado.get();
        Instant ahora = reloj.instant();
        tokens.findPendientesParaActualizar(usuario.getId()).forEach(pendiente -> pendiente.marcarUsado(ahora));

        String codigo = "%06d".formatted(aleatorio.nextInt(1_000_000));
        Instant venceEn = ahora.plus(VIGENCIA);
        tokens.save(TokenRecuperacion.emitir(usuario.getId(), hash(codigo), venceEn));
        envio.enviar(correoNormalizado, codigo, venceEn);
        auditarExito(usuario, SOLICITUD);
    }

    /**
     * Cambia la contraseña si el código más reciente sigue vigente y coincide. Un
     * intento fallido se guarda aunque se lance la excepción, por eso no revierte.
     */
    @Transactional(noRollbackFor = CodigoRecuperacionInvalidoException.class)
    public void confirmar(String correo, String codigo, String contrasenaNueva) {
        Optional<Usuario> encontrado = usuarios.findByCorreo(normalizar(correo));
        if (encontrado.isEmpty()) {
            throw rechazar("correo_no_registrado");
        }

        Usuario usuario = encontrado.get();
        Instant ahora = reloj.instant();
        List<TokenRecuperacion> pendientes = tokens.findPendientesParaActualizar(usuario.getId());
        TokenRecuperacion token = pendientes.isEmpty() ? null : pendientes.get(0);
        if (token == null || !token.estaVigente(ahora)) {
            throw rechazar("codigo_vencido_o_usado");
        }
        if (!coincide(codigo, token.getCodigoHash())) {
            token.registrarIntentoFallido();
            throw rechazar("codigo_erroneo");
        }

        token.marcarUsado(ahora);
        usuario.cambiarContrasena(codificador.encode(contrasenaNueva));
        auditarExito(usuario, CONFIRMACION);
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean coincide(String codigo, String hashGuardado) {
        // Comparación en tiempo constante para no filtrar cuántos caracteres acertaron.
        return MessageDigest.isEqual(hash(codigo).getBytes(StandardCharsets.US_ASCII),
                hashGuardado.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hash(String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException imposible) {
            // Toda JVM está obligada a incluir SHA-256.
            throw new IllegalStateException(imposible);
        }
    }

    private CodigoRecuperacionInvalidoException rechazar(String motivo) {
        auditarFallo(CONFIRMACION, motivo);
        return new CodigoRecuperacionInvalidoException();
    }

    private void auditarExito(Usuario usuario, String operacion) {
        publicador.publicar(new EventoCritico(String.valueOf(usuario.getId()), AccionCritica.AUTENTICACION,
                ENTIDAD, String.valueOf(usuario.getId()), ResultadoEvento.EXITO, reloj.instant(),
                Map.of(OPERACION, operacion)));
    }

    private void auditarFallo(String operacion, String motivo) {
        publicador.publicar(new EventoCritico(null, AccionCritica.AUTENTICACION, ENTIDAD, null,
                ResultadoEvento.FALLO, reloj.instant(), Map.of(OPERACION, operacion, "motivo", motivo)));
    }
}
