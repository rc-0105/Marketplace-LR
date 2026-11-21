package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;

/**
 * Una reserva tal como la ve su huésped (SOF-35): su {@code estadoActual} y el alojamiento, que es
 * {@code null} si la publicación ya no existe.
 */
public record ReservaConsultada(Reserva reserva, EstadoReserva estadoActual, AlojamientoReservado alojamiento) {
}
