package co.edu.unbosque.marketplace.identidad.controllers.dto;

import co.edu.unbosque.marketplace.identidad.model.Usuario;

/** Perfil propio (RF-07): nunca incluye la contraseña ni su hash. */
public record PerfilResponse(Long id, String nombre, String correo, String telefono, String fotoUrl, String rol) {

    public static PerfilResponse desde(Usuario usuario) {
        return new PerfilResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getTelefono(),
                usuario.getFotoUrl(), usuario.getRol().name());
    }
}
