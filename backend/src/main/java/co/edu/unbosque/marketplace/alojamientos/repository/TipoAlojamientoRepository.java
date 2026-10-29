package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoAlojamientoRepository extends JpaRepository<TipoAlojamiento, Long> {

    List<TipoAlojamiento> findAllByOrderByNombreAsc();
}
