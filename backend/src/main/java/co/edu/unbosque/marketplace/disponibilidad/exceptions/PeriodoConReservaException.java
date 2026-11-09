package co.edu.unbosque.marketplace.disponibilidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Una reserva confirmada ocupa el periodo; la respuesta indica cuál en {@code reservaId} (RF-24). */
public class PeriodoConReservaException extends NegocioException {

    public PeriodoConReservaException(Long reservaId) {
        super(HttpStatus.CONFLICT,
                "El periodo tiene la reserva %d confirmada; no se puede modificar ni eliminar.".formatted(reservaId),
                Map.of("reservaId", reservaId));
    }
}
