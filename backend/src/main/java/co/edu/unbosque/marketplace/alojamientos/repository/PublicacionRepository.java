package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {
}
