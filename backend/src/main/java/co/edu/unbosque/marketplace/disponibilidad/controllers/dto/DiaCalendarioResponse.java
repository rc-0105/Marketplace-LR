package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import co.edu.unbosque.marketplace.disponibilidad.model.DiaCalendario;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/** Un día del calendario; {@code reservaId} no aparece en el JSON salvo en la vista del dueño. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiaCalendarioResponse(LocalDate fecha, String estado, Long reservaId) {

    public static DiaCalendarioResponse desde(DiaCalendario dia) {
        return new DiaCalendarioResponse(dia.fecha(), dia.estado().name(), dia.reservaId());
    }
}
