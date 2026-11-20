package co.edu.unbosque.marketplace.auditoria.services;

import co.edu.unbosque.marketplace.auditoria.model.EventoAuditoria;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Arma la consulta con solo los filtros presentes; sin concatenar SQL (D8, D9). */
final class EspecificacionesAuditoria {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private EspecificacionesAuditoria() {
    }

    static Specification<EventoAuditoria> de(FiltroAuditoria filtro) {
        List<Specification<EventoAuditoria>> presentes = new ArrayList<>();
        if (filtro.usuario() != null) {
            presentes.add(igual("usuario", filtro.usuario()));
        }
        if (filtro.evento() != null) {
            presentes.add(igual("accion", filtro.evento()));
        }
        if (filtro.entidad() != null) {
            presentes.add(igual("entidad", filtro.entidad()));
        }
        if (filtro.entidadId() != null) {
            presentes.add(igual("entidadId", filtro.entidadId()));
        }
        if (filtro.desde() != null) {
            Instant inicio = filtro.desde().atStartOfDay(ZONA).toInstant();
            presentes.add((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("fecha"), inicio));
        }
        if (filtro.hasta() != null) {
            Instant fin = diaSiguiente(filtro.hasta());
            presentes.add((raiz, consulta, cb) -> cb.lessThan(raiz.get("fecha"), fin));
        }
        return Specification.allOf(presentes);
    }

    // hasta es inclusivo del día completo: la fecha debe ser anterior a las 00:00 del día siguiente.
    private static Instant diaSiguiente(LocalDate hasta) {
        return hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }

    private static Specification<EventoAuditoria> igual(String campo, String valor) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get(campo), valor);
    }
}
