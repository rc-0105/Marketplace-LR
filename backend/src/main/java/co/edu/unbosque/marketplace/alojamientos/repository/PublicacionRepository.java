package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    @Override
    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    Optional<Publicacion> findById(Long id);

    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    List<Publicacion> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);

    @EntityGraph(attributePaths = {"tipo", "imagenes"})
    List<Publicacion> findByEstadoAndCiudadIgnoreCaseOrderById(EstadoPublicacion estado, String ciudad);

    @EntityGraph(attributePaths = {"tipo", "imagenes"})
    List<Publicacion> findByEstadoOrderByIdDesc(EstadoPublicacion estado);

    Optional<AnfitrionDePublicacion> findAnfitrionById(Long id);

    Optional<AccesoDePublicacion> findAccesoById(Long id);


    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    List<Publicacion> findByEstado(EstadoPublicacion estado);

    /**
     * Moderación (SOF-83): publicaciones en los estados pedidos cuyo título contiene el texto, ordenadas por
     * título. El texto llega con {@code \}, {@code %} y {@code _} escapados para que se busque literalmente.
     */
    @Query("""
            SELECT p FROM Publicacion p
            WHERE p.estado IN :estados
              AND LOWER(p.titulo) LIKE LOWER(CONCAT('%', :texto, '%')) ESCAPE '\\'
            ORDER BY p.titulo, p.id
            """)
    List<Publicacion> buscarParaModeracion(@Param("texto") String texto,
            @Param("estados") Collection<EstadoPublicacion> estados, Pageable pagina);
}
