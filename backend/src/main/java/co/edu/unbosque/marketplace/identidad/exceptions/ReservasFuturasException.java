package co.edu.unbosque.marketplace.identidad.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** La cuenta tiene reservas confirmadas pendientes y no puede darse de baja; el número va en el detalle y aparte. */
public class ReservasFuturasException extends NegocioException {

    private static final String CODIGO = "RESERVAS_FUTURAS_ACTIVAS";
    private static final String DETALLE =
            "Tienes reservas confirmadas pendientes (%d). Cancélalas o espera a que terminen antes de continuar.";

    public ReservasFuturasException(long reservasBloqueantes) {
        super(HttpStatus.CONFLICT, DETALLE.formatted(reservasBloqueantes),
                Map.of("codigo", CODIGO, "reservasBloqueantes", reservasBloqueantes));
    }
}
