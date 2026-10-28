package co.edu.unbosque.marketplace.identidad.controllers.dto;

/** Siempre el mismo mensaje, exista o no el correo (anti-enumeración). */
public record RecuperacionSolicitadaResponse(String mensaje) {

    public static final RecuperacionSolicitadaResponse GENERICA = new RecuperacionSolicitadaResponse(
            "Si el correo está registrado, te enviamos un código para restablecer la contraseña.");
}
