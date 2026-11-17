package co.edu.unbosque.marketplace.disponibilidad.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendario de una publicación en la ventana semiabierta {@code [desde, hasta)}: {@code hasta} no
 * aparece en {@code dias}. {@code tienePeriodos} no depende de la ventana: dice si la publicación tiene
 * algún periodo disponible registrado.
 */
public record Calendario(Long publicacionId, LocalDate desde, LocalDate hasta, boolean tienePeriodos,
        List<DiaCalendario> dias) {
}
