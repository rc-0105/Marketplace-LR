package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.time.LocalDate;

public record PeriodoResponse(Long id, Long publicacionId, LocalDate fechaInicio, LocalDate fechaFin) {

    public static PeriodoResponse desde(PeriodoDisponible periodo) {
        return new PeriodoResponse(periodo.getId(), periodo.getPublicacionId(), periodo.getFechaInicio(),
                periodo.getFechaFin());
    }
}
