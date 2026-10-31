package co.edu.unbosque.marketplace.identidad.services;

import co.edu.unbosque.marketplace.identidad.exceptions.UsuarioNoEncontradoException;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos básicos del perfil propio (RF-07, RF-08). La contraseña se cambia en {@link CuentaService}. */
@Service
@Transactional
public class PerfilService {

    private final UsuarioRepository usuarios;

    public PerfilService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public Usuario consultar(Long usuarioId) {
        return buscar(usuarioId);
    }

    public Usuario actualizar(Long usuarioId, String nombre, String telefono, String fotoUrl) {
        Usuario usuario = buscar(usuarioId);
        usuario.actualizarDatos(nombre, telefono, fotoUrl);
        return usuarios.save(usuario);
    }

    private Usuario buscar(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(UsuarioNoEncontradoException::new);
    }
}
