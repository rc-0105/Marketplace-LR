package co.edu.unbosque.marketplace.auditoria.services;

import co.edu.unbosque.marketplace.auditoria.exceptions.EventoAuditoriaNoEncontradoException;
import co.edu.unbosque.marketplace.auditoria.exceptions.FiltroAuditoriaInvalidoException;
import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.auditoria.repository.EventoAuditoriaRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import java.util.Arrays;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registro y consulta de los eventos de auditoría (RF-59). */
@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository eventos;

    public AuditoriaService(EventoAuditoriaRepository eventos) {
        this.eventos = eventos;
    }

    /**
     * Guarda el evento en su propia transacción: el rollback del negocio no lo arrastra, así
     * los FALLO se conservan. Limitación aceptada: un EXITO publicado antes de un rollback
     * posterior también queda registrado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(EventoAuditoria evento) {
        eventos.save(evento);
    }

    /** Una página de eventos, de más nuevo a más viejo; el orden no lo elige el cliente. */
    @Transactional(readOnly = true)
    public Page<EventoAuditoria> consultar(FiltroAuditoria filtro, int pagina, int tamano) {
        validar(filtro);
        PageRequest paginacion = PageRequest.of(pagina, tamano,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")));
        return eventos.findAll(EspecificacionesAuditoria.de(filtro), paginacion);
    }

    /** El detalle de un evento; si no existe, 404. */
    @Transactional(readOnly = true)
    public EventoAuditoria obtener(Long id) {
        return eventos.findById(id).orElseThrow(EventoAuditoriaNoEncontradoException::new);
    }

    private static void validar(FiltroAuditoria filtro) {
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw new FiltroAuditoriaInvalidoException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        if (filtro.evento() != null && Arrays.stream(AccionCritica.values())
                .noneMatch(accion -> accion.name().equals(filtro.evento()))) {
            throw new FiltroAuditoriaInvalidoException("El evento \"" + filtro.evento()
                    + "\" no es una acción de auditoría válida.");
        }
    }
}
