package co.edu.unbosque.marketplace.compartido.notificaciones;

/**
 * Puerto para avisarle a un usuario dentro de la plataforma (SOF-37, RF-40); sin correo. Quien lo llama ya
 * completó su operación: registrar la notificación nunca la hace fallar, la implementación atrapa y registra
 * en el log cualquier error.
 */
public interface Notificador {

    void notificar(NuevaNotificacion notificacion);
}
