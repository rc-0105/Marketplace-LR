package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import co.edu.unbosque.marketplace.disponibilidad.model.MotivoNoDisponible;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * La estancia pedida no se ofrece: ningún periodo la contiene o una reserva ocupa esas noches
 * (SOF-33, RF-26, RF-27). Es 409 y no 422 porque la solicitud está bien formada y choca con el
 * estado actual de la publicación, igual que el cruce con otra reserva; el {@code motivo} viaja
 * en la respuesta para que el cliente distinga los dos casos.
 */
public class EstanciaNoDisponibleException extends NegocioException {

    private final transient MotivoNoDisponible motivo;

    public EstanciaNoDisponibleException(MotivoNoDisponible motivo) {
        super(HttpStatus.CONFLICT, mensaje(motivo), Map.of("motivo", motivo.name()));
        this.motivo = motivo;
    }

    public MotivoNoDisponible getMotivo() {
        return motivo;
    }

    private static String mensaje(MotivoNoDisponible motivo) {
        return switch (motivo) {
            case FUERA_DE_PERIODO ->
                    "Esas fechas no están dentro de un periodo en que el anfitrión ofrece el alojamiento.";
            case RESERVADO -> "Esas fechas ya no están disponibles: alguien más reservó primero.";
        };
    }
}
