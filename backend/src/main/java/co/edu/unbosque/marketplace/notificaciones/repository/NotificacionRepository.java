package co.edu.unbosque.marketplace.notificaciones.repository;

import co.edu.unbosque.marketplace.notificaciones.model.Notificacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /** Las 50 más recientes del usuario; el id desempata las creadas en el mismo instante. */
    List<Notificacion> findTop50ByDestinatarioIdOrderByCreadoEnDescIdDesc(Long destinatarioId);

    long countByDestinatarioIdAndLeidaEnIsNull(Long destinatarioId);

    /** Solo la encuentra su destinatario: para otro usuario es como si no existiera. */
    Optional<Notificacion> findByIdAndDestinatarioId(Long id, Long destinatarioId);
}
