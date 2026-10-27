package co.edu.unbosque.marketplace.config;

import org.springframework.security.oauth2.jwt.JwtException;

/**
 * La denylist no se pudo consultar. No es BadJwtException a propósito: el token
 * no es inválido, así que no debe terminar en 401 sino en 503 (D5).
 */
public class SesionNoVerificableException extends JwtException {

    public SesionNoVerificableException(Throwable causa) {
        super("No se pudo verificar la sesión", causa);
    }
}
