package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Reserva confirmada, con el mismo desglose que la cotización que la originó. */
public record ReservaResponse(Long id, Long publicacionId, LocalDate llegada, LocalDate salida,
        EstadoReserva estado, long noches, BigDecimal precioBase, BigDecimal tarifaLimpieza,
        BigDecimal tarifaServicio, BigDecimal descuento, BigDecimal totalServiciosAdicionales, BigDecimal total) {

    public static ReservaResponse desde(Reserva reserva) {
        return new ReservaResponse(reserva.getId(), reserva.getPublicacionId(), reserva.getLlegada(),
                reserva.getSalida(), reserva.getEstado(), reserva.getNoches(), reserva.getPrecioBase(),
                reserva.getTarifaLimpieza(), reserva.getTarifaServicio(), reserva.getDescuento(),
                reserva.getTotalServiciosAdicionales(), reserva.getTotal());
    }
}
