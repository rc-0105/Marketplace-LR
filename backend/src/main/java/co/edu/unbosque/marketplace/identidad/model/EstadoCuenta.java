package co.edu.unbosque.marketplace.identidad.model;

/** Estado de la cuenta (RF-06). Solo una cuenta ACTIVA puede iniciar sesión; ELIMINADA es terminal. */
public enum EstadoCuenta {
    ACTIVA,
    DESACTIVADA,
    RESTRINGIDA,
    ELIMINADA
}
