package co.edu.unbosque.marketplace.identidad.services;

import java.time.Instant;

/** JWT recién firmado junto con los datos que el login necesita devolver. */
public record TokenEmitido(String valor, String jti, Instant expiraEn) {

    @Override
    public String toString() {
        // El token nunca debe terminar en un log (RF-45).
        return "TokenEmitido[jti=%s, expiraEn=%s]".formatted(jti, expiraEn);
    }
}
