package co.edu.unbosque.marketplace.identidad.controllers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos del formulario de registro. La contraseña exige 8 caracteres, una
 * mayúscula, un número y un carácter especial; el máximo de 72 es el límite de BCrypt.
 */
public record RegistrarUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres.")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        @Pattern(regexp = "^(?=.*\\p{Lu})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}\\s]).*$",
                message = "La contraseña debe incluir al menos una mayúscula, un número y un carácter especial.")
        String contrasena,

        @NotBlank(message = "El rol es obligatorio.")
        String rol) {

    @Override
    public String toString() {
        // Que la contraseña nunca termine en un log por accidente.
        return "RegistrarUsuarioRequest[nombre=%s, correo=%s, rol=%s]".formatted(nombre, correo, rol);
    }
}
