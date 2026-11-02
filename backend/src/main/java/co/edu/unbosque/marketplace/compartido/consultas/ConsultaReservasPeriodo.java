package co.edu.unbosque.marketplace.compartido.consultas;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Puerto con el que {@code disponibilidad} pregunta si una reserva confirmada impide modificar o eliminar
 * un periodo disponible, sin conocer el modelo de reservas (ADR-2).
 *
 * <p>Contrato para HU-22: la implementación real MUST ser un {@code @Service} o {@code @Component}
 * escaneado, como la de {@link ConsultaReservasFuturas}. Mientras no exista, {@code
 * ConfiguracionReservasPorDefecto} registra un bean que no encuentra reservas.
 */
public interface ConsultaReservasPeriodo {

    /**
     * Id de una reserva confirmada de la publicación que se cruza con el rango semiabierto
     * {@code [inicio, fin)}, o vacío si ninguna lo ocupa.
     */
    Optional<Long> reservaConfirmadaEn(Long publicacionId, LocalDate inicio, LocalDate fin);
}
