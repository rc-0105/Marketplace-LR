package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.ConfirmacionInvalidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.OperacionCuentaNoPermitidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.ReservasFuturasException;
import co.edu.unbosque.marketplace.identidad.exceptions.UsuarioNoEncontradoException;
import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estado de las cuentas: la administración (RF-06) restringe y levanta; el dueño desactiva la suya (RF-09). */
@Service
@Transactional
public class CuentaService {

    private static final String ENTIDAD = "Usuario";
    private static final String OPERACION = "operacion";
    private static final String RESTRINGIR = "restringir_cuenta";
    private static final String LEVANTAR = "levantar_restriccion";
    private static final String DESACTIVAR = "desactivar_cuenta";
    private static final String ELIMINAR = "eliminar_cuenta";
    private static final String MOTIVO = "motivo";

    private final UsuarioRepository usuarios;
    private final PublicadorEventos publicador;
    private final PasswordEncoder codificador;
    private final ConsultaReservasFuturas reservas;

    public CuentaService(UsuarioRepository usuarios, PublicadorEventos publicador, PasswordEncoder codificador,
            ConsultaReservasFuturas reservas) {
        this.usuarios = usuarios;
        this.publicador = publicador;
        this.codificador = codificador;
        this.reservas = reservas;
    }

    public void restringir(Long usuarioId, String motivo, Long adminId) {
        rechazarSiEsElMismo(usuarioId, adminId, "No puedes restringir tu propia cuenta.");
        Usuario usuario = buscar(usuarioId);
        if (usuario.getEstado() != EstadoCuenta.ACTIVA) {
            throw new OperacionCuentaNoPermitidaException("Solo se puede restringir una cuenta activa.");
        }
        usuario.restringir(motivo);
        usuarios.save(usuario);
        auditar(adminId, usuarioId, RESTRINGIR);
    }

    public void levantarRestriccion(Long usuarioId, Long adminId) {
        rechazarSiEsElMismo(usuarioId, adminId, "No puedes levantar la restricción de tu propia cuenta.");
        Usuario usuario = buscar(usuarioId);
        if (usuario.getEstado() != EstadoCuenta.RESTRINGIDA) {
            throw new OperacionCuentaNoPermitidaException(
                    "Solo se puede levantar la restricción de una cuenta restringida.");
        }
        usuario.levantarRestriccion();
        usuarios.save(usuario);
        auditar(adminId, usuarioId, LEVANTAR);
    }

    /** El dueño desactiva su cuenta. Orden: contraseña, estado ACTIVA, reservas y recién entonces la transición. */
    public void desactivar(Long usuarioId, String contrasena) {
        Usuario usuario = confirmarDueno(usuarioId, contrasena, DESACTIVAR);
        exigirActiva(usuario, DESACTIVAR, "Solo se puede desactivar una cuenta activa.");
        exigirSinReservas(usuario, DESACTIVAR);
        usuario.desactivar();
        usuarios.save(usuario);
        auditarDueno(usuarioId, DESACTIVAR, ResultadoEvento.EXITO, Map.of());
    }

    /**
     * El dueño elimina su cuenta (RF-10): misma confirmación que al desactivar y luego la fila se anonimiza.
     * El hash nuevo viene de un valor aleatorio que nadie conoce, así que la contraseña original deja de servir.
     */
    public void eliminar(Long usuarioId, String contrasena) {
        Usuario usuario = confirmarDueno(usuarioId, contrasena, ELIMINAR);
        exigirActiva(usuario, ELIMINAR, "Solo se puede eliminar una cuenta activa.");
        exigirSinReservas(usuario, ELIMINAR);
        usuario.eliminar(codificador.encode(UUID.randomUUID().toString()));
        usuarios.save(usuario);
        auditarDueno(usuarioId, ELIMINAR, ResultadoEvento.EXITO, Map.of());
    }

    // Antes de ir a la base: un administrador nunca se encierra ni se libera a sí mismo.
    private void rechazarSiEsElMismo(Long usuarioId, Long adminId, String mensaje) {
        if (usuarioId.equals(adminId)) {
            throw new OperacionCuentaNoPermitidaException(mensaje);
        }
    }

    private Usuario buscar(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(UsuarioNoEncontradoException::new);
    }

    // Nunca lleva el texto del motivo: solo quién, a quién y qué operación.
    private void auditar(Long adminId, Long usuarioId, String operacion) {
        publicador.publicar(new EventoCritico(String.valueOf(adminId), AccionCritica.ACCION_ADMINISTRATIVA,
                ENTIDAD, String.valueOf(usuarioId), ResultadoEvento.EXITO, Instant.now(),
                Map.of(OPERACION, operacion)));
    }

    // 403 y no 401: el usuario ya tiene sesión y un 401 se la cerraría; solo se rechaza la acción.
    private Usuario confirmarDueno(Long usuarioId, String contrasena, String operacion) {
        Usuario usuario = buscar(usuarioId);
        if (!codificador.matches(contrasena, usuario.getContrasenaHash())) {
            auditarDueno(usuarioId, operacion, ResultadoEvento.FALLO, Map.of(MOTIVO, "credenciales_invalidas"));
            throw new ConfirmacionInvalidaException();
        }
        return usuario;
    }

    // Por HTTP el filtro de token ya corta las cuentas no activas; esto cubre la carrera con el servicio.
    private void exigirActiva(Usuario usuario, String operacion, String mensaje) {
        if (usuario.getEstado() != EstadoCuenta.ACTIVA) {
            auditarDueno(usuario.getId(), operacion, ResultadoEvento.FALLO, Map.of(MOTIVO, "estado_no_permitido"));
            throw new OperacionCuentaNoPermitidaException(mensaje);
        }
    }

    private void exigirSinReservas(Usuario usuario, String operacion) {
        long pendientes = reservas.contarConfirmadasFuturas(usuario.getId());
        if (pendientes > 0) {
            auditarDueno(usuario.getId(), operacion, ResultadoEvento.FALLO,
                    Map.of(MOTIVO, "reservas_futuras", "reservasBloqueantes", pendientes));
            throw new ReservasFuturasException(pendientes);
        }
    }

    // Quien actúa es el propio dueño. Nunca lleva contraseña, correo, nombre ni hash.
    private void auditarDueno(Long usuarioId, String operacion, ResultadoEvento resultado, Map<String, Object> datos) {
        Map<String, Object> completos = new HashMap<>(datos);
        completos.put(OPERACION, operacion);
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), AccionCritica.AUTENTICACION, ENTIDAD,
                String.valueOf(usuarioId), resultado, Instant.now(), completos));
    }
}
