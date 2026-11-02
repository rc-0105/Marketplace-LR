package co.edu.unbosque.marketplace.disponibilidad.repository;

import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// El cruce entre periodos no se consulta aquí: lo rechaza la restricción
// periodo_disponible_sin_cruce de PostgreSQL (V15).
public interface PeriodoDisponibleRepository extends JpaRepository<PeriodoDisponible, Long> {

    List<PeriodoDisponible> findByPublicacionIdOrderByFechaInicioAsc(Long publicacionId);
}
