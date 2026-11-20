package co.edu.unbosque.marketplace.auditoria.controllers.dto;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.time.Instant;
import java.util.Map;

/** Un evento de auditoría tal como lo ve el administrador, en la lista y en el detalle. */
public record EventoAuditoriaResponse(
        Long id,
        Instant fecha,
        String usuario,
        String accion,
        String entidad,
        String entidadId,
        ResultadoEvento resultado,
        Map<String, Object> datos,
        String correlacion) {

    public static EventoAuditoriaResponse desde(EventoAuditoria evento) {
        return new EventoAuditoriaResponse(evento.getId(), evento.getFecha(), evento.getUsuario(),
                evento.getAccion(), evento.getEntidad(), evento.getEntidadId(), evento.getResultado(),
                evento.getDatos(), evento.getCorrelacion());
    }
}
