package co.edu.unbosque.marketplace.resenas.repository;

import co.edu.unbosque.marketplace.resenas.model.Resena;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistencia de las reseñas (SOF-80). */
public interface ResenaRepository extends JpaRepository<Resena, Long> {

    boolean existsByReservaId(Long reservaId);
}
