package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    @Override
    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    Optional<Publicacion> findById(Long id);

    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    List<Publicacion> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);

    @EntityGraph(attributePaths = {"tipo", "imagenes"})
    List<Publicacion> findByEstadoAndCiudadIgnoreCaseOrderById(EstadoPublicacion estado, String ciudad);

    Optional<AnfitrionDePublicacion> findAnfitrionById(Long id);

    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    List<Publicacion> findByEstado(EstadoPublicacion estado);
}
