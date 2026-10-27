package co.edu.unbosque.marketplace.identidad.model;

/** Roles de la plataforma (ADR-5). */
public enum Rol {
    HUESPED,
    ANFITRION,
    ADMINISTRADOR;

    /** Las cuentas de administrador no se crean desde el registro público. */
    public boolean permiteRegistro() {
        return this != ADMINISTRADOR;
    }
}
