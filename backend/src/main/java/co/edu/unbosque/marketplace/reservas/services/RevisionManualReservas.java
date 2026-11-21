package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.consultas.RevisionReservasPublicacion;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación real del puerto con que {@code alojamientos} envía a revisión manual las reservas de una
 * publicación que bloquea (SOF-83, AC2). "Futuras" usa el mismo "hoy" en Colombia que la cancelación. Se une a
 * la transacción del bloqueo: si el bloqueo falla, las reservas no cambian.
 */
@Service
public class RevisionManualReservas implements RevisionReservasPublicacion {

    private final ReservaRepository reservas;
    private final Clock reloj;

    public RevisionManualReservas(ReservaRepository reservas, Clock reloj) {
        this.reservas = reservas;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public int enviarARevision(Long publicacionId) {
        return reservas.enviarARevision(publicacionId, CancelacionReservaService.hoyEnColombia(reloj));
    }
}
