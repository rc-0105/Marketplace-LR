package co.edu.unbosque.marketplace.disponibilidad.model;

import java.time.LocalDate;

/** Un día del calendario. {@code reservaId} solo viene en los días RESERVADO que el dueño puede ver. */
public record DiaCalendario(LocalDate fecha, EstadoDia estado, Long reservaId) {
}
