package co.edu.unbosque.marketplace.compartido.seguridad;

/**
 * Expresiones para {@code @PreAuthorize} con que cada endpoint declara quién lo
 * usa (ADR-5). Un endpoint de /api/v1 sin esa anotación se niega siempre.
 * Para varios roles: {@code @PreAuthorize("hasAnyRole('ANFITRION', 'ADMINISTRADOR')")}.
 */
public final class Permisos {

    public static final String HUESPED = "hasRole('HUESPED')";
    public static final String ANFITRION = "hasRole('ANFITRION')";
    public static final String ADMINISTRADOR = "hasRole('ADMINISTRADOR')";
    /** Cualquier rol con sesión vigente. */
    public static final String AUTENTICADO = "isAuthenticated()";

    private Permisos() {
    }
}
