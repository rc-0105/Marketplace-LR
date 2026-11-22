package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.services.ReservaDeAnfitrion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una solicitud pendiente de aprobación en la bandeja del anfitrión (SOF-79): fechas, total, el alojamiento, solo el
 * nombre del huésped (nunca correo ni teléfono) y {@code venceEn}, el límite para responder. Título, ciudad e imagen
 * son {@code null} si la publicación ya no existe; {@code huespedNombre} lo es si la cuenta ya no existe.
 */
public record SolicitudReservaResponse(Long id, Long publicacionId, String titulo, String ciudad,
        String imagenPrincipal, String huespedNombre, LocalDate llegada, LocalDate salida, long noches,
        BigDecimal total, Instant venceEn) {

    public static SolicitudReservaResponse desde(ReservaDeAnfitrion fila) {
        Reserva reserva = fila.reserva();
        AlojamientoReservado alojamiento = fila.alojamiento();
        HuespedBasico huesped = fila.huesped();
        return new SolicitudReservaResponse(reserva.getId(), reserva.getPublicacionId(),
                alojamiento == null ? null : alojamiento.titulo(), alojamiento == null ? null : alojamiento.ciudad(),
                alojamiento == null ? null : alojamiento.imagenPrincipal(), huesped == null ? null : huesped.nombre(),
                reserva.getLlegada(), reserva.getSalida(), reserva.getNoches(), reserva.getTotal(),
                reserva.getVenceEn());
    }
}
