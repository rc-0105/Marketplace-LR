package co.edu.unbosque.marketplace.reservas.services;

/**
 * La pasarela no dio un resultado (SOF-34): no respondió dentro del tiempo de espera ({@code sinRespuesta})
 * o respondió con un error que no es de la tarjeta (clave inválida, caída del servicio). No es una excepción
 * de negocio: el servicio de pago la traduce después de dejar la reserva en un estado conocido.
 */
public class FallaPasarelaException extends RuntimeException {

    private final boolean sinRespuesta;

    public FallaPasarelaException(boolean sinRespuesta, String detalle, Throwable causa) {
        super(detalle, causa);
        this.sinRespuesta = sinRespuesta;
    }

    public boolean isSinRespuesta() {
        return sinRespuesta;
    }
}
