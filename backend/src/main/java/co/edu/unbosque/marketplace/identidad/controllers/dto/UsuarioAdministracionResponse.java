package co.edu.unbosque.marketplace.identidad.controllers.dto;

import co.edu.unbosque.marketplace.identidad.model.Usuario;

/** Lo que ve el administrador de una cuenta: incluye el motivo de la restricción, nunca la contraseña. */
public record UsuarioAdministracionResponse(Long id, String nombre, String correo, String rol, String estado,
        String motivoRestriccion) {

    public static UsuarioAdministracionResponse desde(Usuario usuario) {
        return new UsuarioAdministracionResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getRol().name(), usuario.getEstado().name(), usuario.getMotivoRestriccion());
    }
}
