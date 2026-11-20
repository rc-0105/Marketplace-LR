package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import co.edu.unbosque.marketplace.disponibilidad.model.Calendario;
import java.time.LocalDate;
import java.util.List;

/** Calendario en la ventana semiabierta {@code [desde, hasta)}: {@code hasta} no aparece en {@code dias}. */
public record CalendarioResponse(Long publicacionId, LocalDate desde, LocalDate hasta, boolean tienePeriodos,
        List<DiaCalendarioResponse> dias) {

    public static CalendarioResponse desde(Calendario calendario) {
        return new CalendarioResponse(calendario.publicacionId(), calendario.desde(), calendario.hasta(),
                calendario.tienePeriodos(), calendario.dias().stream().map(DiaCalendarioResponse::desde).toList());
    }
}
