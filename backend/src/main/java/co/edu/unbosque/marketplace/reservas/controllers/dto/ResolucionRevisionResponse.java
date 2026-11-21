package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.reservas.services.ResolucionRevision;
import java.math.BigDecimal;

/** Cómo quedó la reserva resuelta (SOF-83); {@code valorDevolucion} es nulo al confirmar. */
public record ResolucionRevisionResponse(Long reservaId, String estado, boolean reembolsado,
        BigDecimal valorDevolucion) {

    public static ResolucionRevisionResponse desde(ResolucionRevision resolucion) {
        return new ResolucionRevisionResponse(resolucion.reservaId(), resolucion.estado().name(),
                resolucion.reembolsado(), resolucion.valorDevolucion());
    }
}
