package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La solicitud de reserva no admite la respuesta del anfitrión (SOF-79): ya no está pendiente de aprobación (venció,
 * se respondió o la canceló el huésped, también si perdió la carrera con otra de esas transiciones) o la publicación
 * está bloqueada. Responde 409; el mensaje distingue el motivo.
 */
public class SolicitudNoRespondibleException extends NegocioException {

    public SolicitudNoRespondibleException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
