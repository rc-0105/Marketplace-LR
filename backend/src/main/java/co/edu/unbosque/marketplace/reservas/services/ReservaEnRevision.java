package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.reservas.model.Reserva;

/**
 * Una reserva EN_REVISION_MANUAL con su alojamiento (SOF-83): título y motivo del bloqueo. {@code alojamiento}
 * es nulo si la publicación ya no existe.
 */
public record ReservaEnRevision(Reserva reserva, AlojamientoReservado alojamiento) {
}
