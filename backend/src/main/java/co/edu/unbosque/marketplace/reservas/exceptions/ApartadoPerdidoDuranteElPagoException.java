package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La pasarela aprobó el cobro, pero la reserva dejó de estar PENDIENTE mientras se procesaba (venció el
 * apartado u otro intento la resolvió primero), así que no se confirmó (SOF-34). El cobro se reembolsa; si
 * el reembolso falló, queda registrado en el log para revisarlo a mano.
 */
public class ApartadoPerdidoDuranteElPagoException extends NegocioException {

    public ApartadoPerdidoDuranteElPagoException(boolean reembolsado) {
        super(HttpStatus.CONFLICT, "El apartado de la reserva venció mientras se procesaba el pago: "
                + (reembolsado
                        ? "el cobro fue reembolsado."
                        : "el reembolso del cobro quedó pendiente y será revisado."));
    }
}
