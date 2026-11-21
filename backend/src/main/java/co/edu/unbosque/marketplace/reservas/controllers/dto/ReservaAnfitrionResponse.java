package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.services.ReservaDeAnfitrion;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del listado del anfitrión (SOF-36): estado actual, fechas, total, el alojamiento y solo el
 * nombre del huésped — nunca su correo ni su teléfono (requisito de minimización de datos). Título,
 * ciudad e imagen son {@code null} si la publicación ya no existe; {@code huespedNombre} es {@code null}
 * si la cuenta ya no existe, el mismo convenio que {@code ResumenReservaResponse}.
 *
 * <p>{@code estado} es un {@code String} derivado de {@link Reserva#estadoActual}, no el enum
 * {@code EstadoReserva}: el contrato no enumera los estados contra un conjunto cerrado, así que un
 * estado futuro (p. ej. {@code COMPLETADA}, SOF-78) aparece sin cambiar este DTO (D6).
 */
public record ReservaAnfitrionResponse(Long id, Long publicacionId, String titulo, String ciudad,
        String imagenPrincipal, String huespedNombre, LocalDate llegada, LocalDate salida, long noches,
        String estado, BigDecimal total) {

    public static ReservaAnfitrionResponse desde(ReservaDeAnfitrion fila) {
        Reserva reserva = fila.reserva();
        AlojamientoReservado alojamiento = fila.alojamiento();
        HuespedBasico huesped = fila.huesped();
        return new ReservaAnfitrionResponse(reserva.getId(), reserva.getPublicacionId(),
                alojamiento == null ? null : alojamiento.titulo(), alojamiento == null ? null : alojamiento.ciudad(),
                alojamiento == null ? null : alojamiento.imagenPrincipal(), huesped == null ? null : huesped.nombre(),
                reserva.getLlegada(), reserva.getSalida(), reserva.getNoches(), fila.estadoActual().name(),
                reserva.getTotal());
    }
}
