package co.edu.unbosque.marketplace.disponibilidad.repository;

import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodoDisponibleRepository extends JpaRepository<PeriodoDisponible, Long> {

    List<PeriodoDisponible> findByPublicacionIdOrderByFechaInicioAsc(Long publicacionId);

    /**
     * Si otro periodo de la publicación comparte alguna noche con {@code [inicio, fin)}.
     * {@code excluirId} es el periodo que se está modificando; al registrar uno nuevo va 0.
     */
    @Query("""
            select count(p) > 0 from PeriodoDisponible p
            where p.publicacionId = :publicacionId and p.id <> :excluirId
              and p.fechaInicio < :fin and p.fechaFin > :inicio
            """)
    boolean existeCruce(@Param("publicacionId") Long publicacionId, @Param("excluirId") Long excluirId,
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}
