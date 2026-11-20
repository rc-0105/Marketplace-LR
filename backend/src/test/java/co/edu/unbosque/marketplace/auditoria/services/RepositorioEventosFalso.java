package co.edu.unbosque.marketplace.auditoria.services;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import co.edu.unbosque.marketplace.auditoria.repository.EventoAuditoriaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Repositorio en memoria para pruebas unitarias; cuenta las consultas para afirmar que no se hicieron. */
public class RepositorioEventosFalso implements EventoAuditoriaRepository {

    public final List<EventoAuditoria> guardados = new ArrayList<>();
    public int consultas;

    @Override
    public EventoAuditoria save(EventoAuditoria evento) {
        guardados.add(evento);
        return evento;
    }

    @Override
    public Optional<EventoAuditoria> findById(Long id) {
        consultas++;
        return guardados.stream().filter(evento -> id.equals(evento.getId())).findFirst();
    }

    @Override
    public Page<EventoAuditoria> findAll(Specification<EventoAuditoria> especificacion, Pageable paginacion) {
        consultas++;
        // No evalúa la especificación: solo sirve para afirmar si se consultó o no.
        return new PageImpl<>(guardados, paginacion, guardados.size());
    }
}
