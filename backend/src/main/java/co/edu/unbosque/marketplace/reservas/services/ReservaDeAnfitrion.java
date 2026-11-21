package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;

/**
 * Una reserva tal como la ve el anfitrión dueño de la publicación (SOF-36): su {@code estadoActual}, el
 * alojamiento y el huésped, ambos {@code null} si la publicación o la cuenta ya no existen — el mismo
 * convenio que {@link ReservaConsultada} para el huésped.
 */
public record ReservaDeAnfitrion(Reserva reserva, EstadoReserva estadoActual, AlojamientoReservado alojamiento,
        HuespedBasico huesped) {
}
