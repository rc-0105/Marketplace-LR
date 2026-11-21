package co.edu.unbosque.marketplace.compartido.consultas;

import java.util.Optional;

/**
 * Puerto con el que {@code resenas} consulta una reserva sin conocer el modelo de reservas (ADR-2). La
 * implementación vive en {@code reservas}.
 */
public interface ConsultaReservaParaResena {

    Optional<ReservaParaResena> consultar(Long reservaId);
}
