package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Long> {

    List<PoliticaCancelacion> findAllByOrderByDiasSinCostoAsc();
}
