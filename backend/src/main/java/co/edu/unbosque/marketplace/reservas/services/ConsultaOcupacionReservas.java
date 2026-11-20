package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación real de los puertos con que {@code disponibilidad} pregunta por reservas (SOF-33): ocupan
 * noches las CONFIRMADA y las PENDIENTE cuyo apartado sigue vigente (ADR-4). El mismo criterio sirve para
 * bloquear la modificación de un periodo: mientras alguien tiene apartadas las noches, el anfitrión no
 * puede quitárselas.
 */
@Service
public class ConsultaOcupacionReservas implements ConsultaReservasPeriodo, ConsultaReservasCalendario {

    private final ReservaRepository reservas;
    private final Clock reloj;

    public ConsultaOcupacionReservas(ReservaRepository reservas, Clock reloj) {
        this.reservas = reservas;
        this.reloj = reloj;
    }

    // El nombre del puerto habla de "confirmada" por la HU-22 original; hoy cubre toda reserva que ocupa
    // noches (CONFIRMADA o PENDIENTE vigente), que es lo que el anfitrión no puede dejar sin periodo.
    @Override
    @Transactional(readOnly = true)
    public Optional<Long> reservaConfirmadaEn(Long publicacionId, LocalDate inicio, LocalDate fin) {
        return reservas.findOcupantes(publicacionId, inicio, fin, Instant.now(reloj)).stream().findFirst()
                .map(Reserva::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaOcupada> ocupantesEntre(Long publicacionId, LocalDate desde, LocalDate hasta) {
        return reservas.findOcupantes(publicacionId, desde, hasta, Instant.now(reloj)).stream()
                .map(reserva -> new ReservaOcupada(reserva.getId(), reserva.getLlegada(), reserva.getSalida()))
                .toList();
    }

    // Una sola consulta por lote en vez de iterar ocupantesEntre una vez por publicación.
    @Override
    @Transactional(readOnly = true)
    public Set<Long> publicacionesOcupadas(Collection<Long> publicacionIds, LocalDate desde, LocalDate hasta) {
        if (publicacionIds.isEmpty()) {
            return Set.of();
        }
        return reservas.findOcupantesDeVarias(publicacionIds, desde, hasta, Instant.now(reloj)).stream()
                .map(Reserva::getPublicacionId).collect(Collectors.toUnmodifiableSet());
    }
}
