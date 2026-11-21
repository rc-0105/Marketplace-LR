package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.services.ReservaConsultada;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de "mis reservas" (SOF-35): estado actual, fechas, total y el alojamiento para reconocerla. Título,
 * ciudad e imagen son {@code null} si la publicación ya no existe.
 */
public record ResumenReservaResponse(Long id, Long publicacionId, String titulo, String ciudad,
        String imagenPrincipal, LocalDate llegada, LocalDate salida, long noches, EstadoReserva estado,
        BigDecimal total) {

    public static ResumenReservaResponse desde(ReservaConsultada consultada) {
        Reserva reserva = consultada.reserva();
        AlojamientoReservado alojamiento = consultada.alojamiento();
        return new ResumenReservaResponse(reserva.getId(), reserva.getPublicacionId(),
                alojamiento == null ? null : alojamiento.titulo(), alojamiento == null ? null : alojamiento.ciudad(),
                alojamiento == null ? null : alojamiento.imagenPrincipal(), reserva.getLlegada(), reserva.getSalida(),
                reserva.getNoches(), consultada.estadoActual(), reserva.getTotal());
    }
}
