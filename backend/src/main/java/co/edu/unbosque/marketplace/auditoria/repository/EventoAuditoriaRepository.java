package co.edu.unbosque.marketplace.auditoria.repository;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;

/**
 * Acceso de solo inserción y lectura al registro de auditoría: no declara ningún
 * método de modificación ni de borrado (REQ-RA7).
 */
public interface EventoAuditoriaRepository extends Repository<EventoAuditoria, Long> {

    EventoAuditoria save(EventoAuditoria evento);

    Optional<EventoAuditoria> findById(Long id);

    Page<EventoAuditoria> findAll(Specification<EventoAuditoria> especificacion, Pageable paginacion);
}
