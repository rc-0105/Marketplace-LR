package co.edu.unbosque.marketplace.notificaciones.services;

import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.notificaciones.exceptions.NotificacionNoEncontradaException;
import co.edu.unbosque.marketplace.notificaciones.model.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.repository.NotificacionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro, consulta y lectura de las notificaciones dentro de la plataforma (SOF-37, RF-40, RF-41). Es la
 * implementación real del puerto {@link Notificador}: un {@code @Service} escaneado, así la provisional de
 * {@code ConfiguracionNotificacionesPorDefecto} no se crea.
 */
@Service
public class NotificacionService implements Notificador {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificaciones;
    private final RegistroNotificaciones registro;
    private final Clock reloj;

    public NotificacionService(NotificacionRepository notificaciones, RegistroNotificaciones registro, Clock reloj) {
        this.notificaciones = notificaciones;
        this.registro = registro;
        this.reloj = reloj;
    }

    /** Nunca lanza: quien avisa ya completó su operación y un aviso perdido solo queda en el log. */
    @Override
    public void notificar(NuevaNotificacion notificacion) {
        try {
            registro.guardar(Notificacion.nueva(notificacion, Instant.now(reloj)));
        } catch (RuntimeException e) {
            // Sin el texto: puede llevar el motivo de una restricción.
            LOG.error("No se pudo registrar la notificacion: tipo={}, destinatario={}, entidad={}, entidadId={}",
                    notificacion.tipo(), notificacion.destinatarioId(), notificacion.entidad(),
                    notificacion.entidadId(), e);
        }
    }

    /** Las 50 más recientes del usuario, de la más nueva a la más vieja, y cuántas tiene sin leer. */
    @Transactional(readOnly = true)
    public BandejaNotificaciones listar(Long usuarioId) {
        return new BandejaNotificaciones(notificaciones.countByDestinatarioIdAndLeidaEnIsNull(usuarioId),
                notificaciones.findTop50ByDestinatarioIdOrderByCreadoEnDescIdDesc(usuarioId));
    }

    /** Marca leída una notificación propia; una ajena o inexistente responde 404. Repetirlo no cambia nada. */
    @Transactional
    public Notificacion marcarLeida(Long notificacionId, Long usuarioId) {
        Notificacion notificacion = notificaciones.findByIdAndDestinatarioId(notificacionId, usuarioId)
                .orElseThrow(NotificacionNoEncontradaException::new);
        notificacion.marcarLeida(Instant.now(reloj));
        return notificacion;
    }
}
