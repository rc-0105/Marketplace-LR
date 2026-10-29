package co.edu.unbosque.marketplace.identidad;

import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de usuarios en cualquier estado, sin pasar por las transiciones del modelo. */
public final class UsuariosDePrueba {

    private UsuariosDePrueba() {
    }

    public static Usuario activa(Long id) {
        return conEstado(id, EstadoCuenta.ACTIVA, null);
    }

    public static Usuario desactivada(Long id) {
        return conEstado(id, EstadoCuenta.DESACTIVADA, null);
    }

    public static Usuario eliminada(Long id) {
        return conEstado(id, EstadoCuenta.ELIMINADA, null);
    }

    public static Usuario restringida(Long id, String motivo) {
        return conEstado(id, EstadoCuenta.RESTRINGIDA, motivo);
    }

    public static Usuario conEstado(Long id, EstadoCuenta estado, String motivo) {
        Usuario usuario = Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash-real", Rol.HUESPED);
        ReflectionTestUtils.setField(usuario, "id", id);
        ReflectionTestUtils.setField(usuario, "estado", estado);
        ReflectionTestUtils.setField(usuario, "motivoRestriccion", motivo);
        return usuario;
    }
}
