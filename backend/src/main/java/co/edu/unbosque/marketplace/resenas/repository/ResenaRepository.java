package co.edu.unbosque.marketplace.resenas.repository;

import co.edu.unbosque.marketplace.resenas.model.EstadoResena;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistencia de las reseñas (SOF-80, SOF-81). */
public interface ResenaRepository extends JpaRepository<Resena, Long> {

    boolean existsByReservaId(Long reservaId);

    /** Las reseñas de una publicación con ese estado, de la más reciente a la más antigua (SOF-81). */
    List<Resena> findByPublicacionIdAndEstadoOrderByFechaDesc(Long publicacionId, EstadoResena estado);
}
