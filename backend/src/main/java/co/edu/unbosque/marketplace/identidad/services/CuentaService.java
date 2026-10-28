package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.OperacionCuentaNoPermitidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.UsuarioNoEncontradoException;
import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración del estado de las cuentas (RF-06): restringir y levantar la restricción. */
@Service
@Transactional
public class CuentaService {

    private static final String ENTIDAD = "Usuario";
    private static final String OPERACION = "operacion";
    private static final String RESTRINGIR = "restringir_cuenta";
    private static final String LEVANTAR = "levantar_restriccion";

    private final UsuarioRepository usuarios;
    private final PublicadorEventos publicador;

    public CuentaService(UsuarioRepository usuarios, PublicadorEventos publicador) {
        this.usuarios = usuarios;
        this.publicador = publicador;
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
}
