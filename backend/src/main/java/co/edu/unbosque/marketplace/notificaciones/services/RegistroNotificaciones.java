package co.edu.unbosque.marketplace.notificaciones.services;

import co.edu.unbosque.marketplace.notificaciones.model.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.repository.NotificacionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda cada notificación en su propia transacción. Vive aparte de {@link NotificacionService} para que el
 * fallo del commit, que ocurre al salir de este método, se atrape allá y nunca llegue a quien avisa.
 * Limitación aceptada, la misma de la auditoría: si el negocio hace rollback después, el aviso queda.
 */
@Component
public class RegistroNotificaciones {

    private final NotificacionRepository notificaciones;

    public RegistroNotificaciones(NotificacionRepository notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(Notificacion notificacion) {
        notificaciones.save(notificacion);
    }
}
