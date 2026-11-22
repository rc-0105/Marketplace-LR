package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.reservas.model.EstadoPago;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.services.ReservaConsultada;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Una reserva abierta por su huésped (SOF-35, AC2): el alojamiento ({@code null} si la publicación ya no
 * existe), el desglose que quedó fijo al reservar, el estado actual y el del pago. Los servicios adicionales no
 * guardan su precio por separado, solo el total; cada uno trae el nombre que tiene hoy, o {@code null} si el
 * anfitrión lo borró.
 */
public record DetalleReservaResponse(Long id, Alojamiento alojamiento, LocalDate llegada, LocalDate salida,
        EstadoReserva estado, Instant venceEn, long noches, BigDecimal precioBase, BigDecimal tarifaLimpieza,
        BigDecimal tarifaServicio, BigDecimal descuento, List<ServicioReservado> serviciosAdicionales,
        BigDecimal totalServiciosAdicionales, BigDecimal total, EstadoPago estadoPago, String transaccionId,
        String motivoRechazo) {

    public record Alojamiento(Long id, String titulo, String ciudad, String imagenPrincipal) {
    }

    public record ServicioReservado(Long id, String nombre) {
    }

    public static DetalleReservaResponse desde(ReservaConsultada consultada) {
        Reserva reserva = consultada.reserva();
        AlojamientoReservado alojamiento = consultada.alojamiento();
        Map<Long, String> nombres = alojamiento == null ? Map.of() : alojamiento.serviciosAdicionales();
        List<ServicioReservado> servicios = reserva.getServiciosAdicionalesIds().stream().sorted()
                .map(id -> new ServicioReservado(id, nombres.get(id))).toList();
        return new DetalleReservaResponse(reserva.getId(),
                alojamiento == null ? null : new Alojamiento(alojamiento.id(), alojamiento.titulo(),
                        alojamiento.ciudad(), alojamiento.imagenPrincipal()),
                reserva.getLlegada(), reserva.getSalida(), consultada.estadoActual(), reserva.getVenceEn(),
                reserva.getNoches(), reserva.getPrecioBase(), reserva.getTarifaLimpieza(), reserva.getTarifaServicio(),
                reserva.getDescuento(), servicios, reserva.getTotalServiciosAdicionales(), reserva.getTotal(),
                reserva.getEstadoPago(), reserva.getTransaccionId(), reserva.getMotivoRechazo());
    }
}
