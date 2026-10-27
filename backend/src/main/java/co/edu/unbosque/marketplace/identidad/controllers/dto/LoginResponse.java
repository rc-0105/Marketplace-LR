package co.edu.unbosque.marketplace.identidad.controllers.dto;

import co.edu.unbosque.marketplace.identidad.services.SesionIniciada;
import java.time.Instant;

/** Respuesta del login: el token solo viaja aquí, y el usuario sin contraseña ni hash. */
public record LoginResponse(String token, String tipo, Instant expiraEn, UsuarioResponse usuario) {

    public static LoginResponse desde(SesionIniciada sesion) {
        return new LoginResponse(sesion.token().valor(), "Bearer", sesion.token().expiraEn(),
                UsuarioResponse.desde(sesion.usuario()));
    }

    @Override
    public String toString() {
        // El token nunca debe terminar en un log (RF-45).
        return "LoginResponse[usuario=%s, expiraEn=%s]".formatted(usuario.id(), expiraEn);
    }
}
