package co.edu.unbosque.marketplace.compartido.seguridad;

import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;

/**
 * Regla de propiedad de los recursos (RF-04): solo su dueño los modifica. La
 * llama el service del módulo con el id del dueño guardado y el id del usuario
 * del token ({@code Long.valueOf(jwt.getSubject())}).
 */
public final class Propiedad {

    private Propiedad() {
    }

    /** Lanza 403 si el recurso no pertenece al usuario; un dueño desconocido también se niega. */
    public static void exigirPropietario(Long propietarioId, Long usuarioId) {
        if (propietarioId == null || !propietarioId.equals(usuarioId)) {
            throw new AccesoDenegadoException();
        }
    }
}
