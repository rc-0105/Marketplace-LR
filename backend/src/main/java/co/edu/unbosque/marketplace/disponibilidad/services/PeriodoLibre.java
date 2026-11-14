package co.edu.unbosque.marketplace.disponibilidad.services;

import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.time.LocalDate;

/** Un periodo en que el alojamiento se ofrece, visible para el huésped. */
public record PeriodoLibre(LocalDate inicio, LocalDate fin) {

    static PeriodoLibre desde(PeriodoDisponible periodo) {
        return new PeriodoLibre(periodo.getFechaInicio(), periodo.getFechaFin());
    }
}
