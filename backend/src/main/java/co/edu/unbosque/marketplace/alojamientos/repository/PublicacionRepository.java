package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    // open-in-view está apagado: la respuesta se arma fuera de la transacción,
    // así que el tipo, la política y las colecciones se traen en la misma consulta.
    // El tipo y la política van explícitos: con un fetch graph, lo que no se
    // nombra queda LAZY aunque la relación sea EAGER.
    @Override
    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    Optional<Publicacion> findById(Long id);

    // Mismo motivo: el listado devuelve la publicación completa y reporta los
    // campos faltantes, que miran servicios e imágenes.
    @EntityGraph(attributePaths = {"tipo", "politica", "servicios", "imagenes", "serviciosAdicionales"})
    List<Publicacion> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);
}
