package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.services.ReservaEnRevision;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de las reservas en revisión manual (SOF-83). {@code titulo} y {@code motivoBloqueo} son nulos si la
 * publicación ya no existe.
 */
public record ReservaEnRevisionResponse(Long id, Long publicacionId, String titulo, Long huespedId, LocalDate llegada,
        LocalDate salida, BigDecimal total, String estadoPago, String motivoBloqueo) {

    public static ReservaEnRevisionResponse desde(ReservaEnRevision enRevision) {
        Reserva reserva = enRevision.reserva();
        AlojamientoReservado alojamiento = enRevision.alojamiento();
        return new ReservaEnRevisionResponse(reserva.getId(), reserva.getPublicacionId(),
                alojamiento == null ? null : alojamiento.titulo(), reserva.getHuespedId(), reserva.getLlegada(),
                reserva.getSalida(), reserva.getTotal(), reserva.getEstadoPago().name(),
                alojamiento == null ? null : alojamiento.motivoBloqueo());
    }
}
