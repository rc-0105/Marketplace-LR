package co.edu.unbosque.marketplace.disponibilidad.repository;

import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodoDisponibleRepository extends JpaRepository<PeriodoDisponible, Long> {

	List<PeriodoDisponible> findByPublicacionIdOrderByFechaInicioAsc(Long publicacionId);

	// Periodos de la publicación que se cruzan con la ventana [desde, hasta): inicio < hasta y fin > desde, de
	// modo que uno que solo la toca por el borde no cuenta. Los argumentos van en el orden del nombre.
	List<PeriodoDisponible> findByPublicacionIdAndFechaInicioLessThanAndFechaFinGreaterThan(Long publicacionId,
			LocalDate hasta, LocalDate desde);

	// Si la publicación tiene algún periodo, en cualquier fecha: el calendario lo usa para avisar que está vacía.
	boolean existsByPublicacionId(Long publicacionId);

	// Periodos de las publicaciones dadas que cubren toda la estancia [llegada,
	// salida): un solo periodo debe contenerla, porque dos contiguos los ofreció el anfitrión por separado.
	List<PeriodoDisponible> findByPublicacionIdInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
			Collection<Long> publicacionIds, LocalDate llegada, LocalDate salida);
}
