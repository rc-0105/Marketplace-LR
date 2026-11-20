package co.edu.unbosque.marketplace.disponibilidad.controllers.dto;

import co.edu.unbosque.marketplace.disponibilidad.model.DisponibilidadRango;
import java.time.LocalDate;

/**
 * Si la estancia está disponible y, si no, por qué ({@code FUERA_DE_PERIODO} o {@code RESERVADO}). Con
 * {@code disponible} en true el {@code motivo} viaja como {@code null}. No incluye datos de la reserva.
 */
public record DisponibilidadRangoResponse(Long publicacionId, LocalDate desde, LocalDate hasta,
        boolean disponible, String motivo) {

    public static DisponibilidadRangoResponse desde(DisponibilidadRango rango) {
        return new DisponibilidadRangoResponse(rango.publicacionId(), rango.desde(), rango.hasta(),
                rango.disponible(), rango.motivo() == null ? null : rango.motivo().name());
    }
}
