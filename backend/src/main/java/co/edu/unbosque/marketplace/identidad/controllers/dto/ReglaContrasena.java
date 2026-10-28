package co.edu.unbosque.marketplace.identidad.controllers.dto;

/**
 * Regla única de la contraseña para registro y recuperación: 8 caracteres, una
 * mayúscula, un número y un carácter especial; el máximo de 72 es el límite de
 * BCrypt. El frontend la replica en {@code models/usuario.js}.
 */
public final class ReglaContrasena {

    public static final int MINIMO = 8;
    public static final int MAXIMO = 72;
    public static final String LONGITUD = "La contraseña debe tener entre 8 y 72 caracteres.";
    public static final String PATRON = "^(?=.*\\p{Lu})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}\\s]).*$";
    public static final String REQUISITOS =
            "La contraseña debe incluir al menos una mayúscula, un número y un carácter especial.";

    private ReglaContrasena() {
    }
}
