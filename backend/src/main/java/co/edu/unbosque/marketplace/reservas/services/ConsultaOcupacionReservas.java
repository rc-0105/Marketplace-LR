package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación real de los puertos con que {@code disponibilidad} pregunta por reservas
 * (SOF-33): PENDIENTE y CONFIRMADA ocupan noches (ADR-4); solo CONFIRMADA bloquea un periodo.
 */
@Service
public class ConsultaOcupacionReservas implements ConsultaReservasPeriodo, ConsultaReservasCalendario {

    private static final Set<EstadoReserva> OCUPAN = Set.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);

    private final ReservaRepository reservas;

    public ConsultaOcupacionReservas(ReservaRepository reservas) {
        this.reservas = reservas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> reservaConfirmadaEn(Long publicacionId, LocalDate inicio, LocalDate fin) {
        return reservas.findFirstByPublicacionIdAndEstadoAndLlegadaLessThanAndSalidaGreaterThan(publicacionId,
                EstadoReserva.CONFIRMADA, fin, inicio).map(Reserva::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaOcupada> ocupantesEntre(Long publicacionId, LocalDate desde, LocalDate hasta) {
        return reservas.findByPublicacionIdAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(publicacionId, OCUPAN,
                hasta, desde).stream().map(reserva -> new ReservaOcupada(reserva.getId(), reserva.getLlegada(),
                reserva.getSalida())).toList();
    }

    // Una sola consulta por lote en vez de iterar ocupantesEntre una vez por publicación.
    @Override
    @Transactional(readOnly = true)
    public Set<Long> publicacionesOcupadas(Collection<Long> publicacionIds, LocalDate desde, LocalDate hasta) {
        if (publicacionIds.isEmpty()) {
            return Set.of();
        }
        return reservas.findByPublicacionIdInAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(publicacionIds,
                OCUPAN, hasta, desde).stream().map(Reserva::getPublicacionId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
