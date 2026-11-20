package co.edu.unbosque.marketplace.auditoria.infraestructura;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.auditoria.services.AuditoriaService;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.infraestructura.web.FiltroCorrelacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Guarda cada evento crítico en la tabla de auditoría (RF-59), junto a
 * {@link RegistroEventosCriticos}, que sigue escribiendo el log JSON como respaldo.
 *
 * <p>Contrato con los publicadores de reserva (REQ-RA8): {@code entidad="Reserva"},
 * {@code entidadId=<reservaId>} y {@code datos.operacion}, para que la consulta por reserva
 * encuentre la secuencia.
 */
@Component
public class PersistenciaEventosCriticos {

    private static final Logger LOG = LoggerFactory.getLogger(PersistenciaEventosCriticos.class);

    private final AuditoriaService auditoria;

    public PersistenciaEventosCriticos(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    // Escucha síncrona a propósito: corre en el hilo de la petición y por eso ve el MDC.
    // La transacción vive en AuditoriaService para que el fallo del commit se atrape aquí.
    @EventListener
    public void persistir(EventoCritico evento) {
        try {
            auditoria.registrar(EventoAuditoria.de(evento, SanitizadorDatos.sanear(evento.datos()),
                    MDC.get(FiltroCorrelacion.CLAVE_MDC)));
        } catch (RuntimeException e) {
            // Nunca se registran los datos del evento: pueden traer información sensible.
            LOG.error("No se pudo persistir el evento de auditoria: accion={}, entidad={}, entidadId={}",
                    evento.accion(), evento.entidad(), evento.entidadId(), e);
        }
    }
}
