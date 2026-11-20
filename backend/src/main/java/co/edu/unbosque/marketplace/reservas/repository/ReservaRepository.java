package co.edu.unbosque.marketplace.reservas.repository;

import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // Reintento idempotente (ADR-4): la misma reserva, del mismo huésped y con las mismas fechas
    // exactas, que todavía no está cancelada.
    Optional<Reserva> findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(Long huespedId,
            Long publicacionId, LocalDate llegada, LocalDate salida, EstadoReserva estadoExcluido);

    // Reservas de la publicación en alguno de los estados dados que se cruzan con la ventana
    // [desde, hasta): llegada < hasta y salida > desde, de modo que una que solo la toca por el
    // borde no cuenta. Los argumentos van en el orden del nombre.
    List<Reserva> findByPublicacionIdAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(Long publicacionId,
            Collection<EstadoReserva> estados, LocalDate hasta, LocalDate desde);

    // Misma regla de cruce, pero para varias publicaciones a la vez: una sola consulta por lote.
    List<Reserva> findByPublicacionIdInAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(
            Collection<Long> publicacionIds, Collection<EstadoReserva> estados, LocalDate hasta, LocalDate desde);

    // Solo las CONFIRMADAS: una PENDIENTE todavía no es la reserva firme que bloquea un periodo.
    Optional<Reserva> findFirstByPublicacionIdAndEstadoAndLlegadaLessThanAndSalidaGreaterThan(Long publicacionId,
            EstadoReserva estado, LocalDate hasta, LocalDate desde);
}
