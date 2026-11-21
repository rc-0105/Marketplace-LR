package co.edu.unbosque.marketplace.reservas.repository;

import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistencia de las cancelaciones registradas (SOF-75). */
public interface CancelacionRepository extends JpaRepository<Cancelacion, Long> {
}
