package co.edu.unbosque.marketplace.reservas.exceptions;

import co.edu.unbosque.marketplace.compartido.exceptions.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * La publicación exige aprobación del anfitrión antes de confirmar (RF-21); SOF-33 solo cubre
 * el modo INMEDIATA, y ese flujo de aprobación queda para una historia futura.
 */
public class ModoReservaNoSoportadoException extends NegocioException {

    public ModoReservaNoSoportadoException() {
        super(HttpStatus.BAD_REQUEST,
                "Esta publicación requiere aprobación del anfitrión; todavía no está soportado.");
    }
}
