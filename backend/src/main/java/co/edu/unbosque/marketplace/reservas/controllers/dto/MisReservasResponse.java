package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.reservas.services.HistorialReservas;
import java.util.List;

/** Reservas del huésped autenticado separadas en futuras y pasadas (SOF-35, AC1). */
public record MisReservasResponse(List<ResumenReservaResponse> futuras, List<ResumenReservaResponse> pasadas) {

    public static MisReservasResponse desde(HistorialReservas historial) {
        return new MisReservasResponse(historial.futuras().stream().map(ResumenReservaResponse::desde).toList(),
                historial.pasadas().stream().map(ResumenReservaResponse::desde).toList());
    }
}
