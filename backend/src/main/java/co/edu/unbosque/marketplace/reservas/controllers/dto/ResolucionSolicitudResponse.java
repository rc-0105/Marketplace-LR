package co.edu.unbosque.marketplace.reservas.controllers.dto;

import co.edu.unbosque.marketplace.reservas.services.ResolucionSolicitud;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Cómo quedó la solicitud tras la respuesta del anfitrión (SOF-79): {@code estado} PENDIENTE (aprobada, con
 * {@code venceEn} el plazo de pago del huésped) o RECHAZADA (con {@code motivo}). Los campos que no aplican se omiten.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResolucionSolicitudResponse(Long reservaId, String estado, Instant venceEn, String motivo) {

    public static ResolucionSolicitudResponse desde(ResolucionSolicitud resolucion) {
        return new ResolucionSolicitudResponse(resolucion.reservaId(), resolucion.estado().name(),
                resolucion.venceEn(), resolucion.motivo());
    }
}
