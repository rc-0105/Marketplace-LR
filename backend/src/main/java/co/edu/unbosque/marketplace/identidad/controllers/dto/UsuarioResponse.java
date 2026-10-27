package co.edu.unbosque.marketplace.identidad.controllers.dto;

import co.edu.unbosque.marketplace.identidad.model.Usuario;

/** Vista pública de una cuenta: nunca incluye la contraseña ni su hash. */
public record UsuarioResponse(Long id, String nombre, String correo, String rol, String estado) {

    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getRol().name(), usuario.getEstado().name());
    }
}
