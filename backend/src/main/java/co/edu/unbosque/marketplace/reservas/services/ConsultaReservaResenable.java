package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservaParaResena;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaParaResena;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Entrega a {@code resenas} los datos de una reserva que necesita para validar la reseña (SOF-80). */
@Service
public class ConsultaReservaResenable implements ConsultaReservaParaResena {

    private final ReservaRepository reservas;

    public ConsultaReservaResenable(ReservaRepository reservas) {
        this.reservas = reservas;
    }

    @Override
    public Optional<ReservaParaResena> consultar(Long reservaId) {
        Optional<Reserva> encontrada = reservas.findById(reservaId);
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        Reserva reserva = encontrada.get();
        return Optional.of(new ReservaParaResena(reserva.getId(), reserva.getHuespedId(),
                reserva.getPublicacionId(), reserva.getEstado().name()));
    }
}
