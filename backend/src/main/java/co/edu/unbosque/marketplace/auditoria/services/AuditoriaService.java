package co.edu.unbosque.marketplace.auditoria.services;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.auditoria.repository.EventoAuditoriaRepository;
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
}
