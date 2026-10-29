package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    // open-in-view está apagado: la respuesta se arma fuera de la transacción,
    // así que el tipo, los servicios y las imágenes se traen en la misma consulta.
    // El tipo va explícito: con un fetch graph, lo que no se nombra queda LAZY
    // aunque la relación sea EAGER.
    @Override
    @EntityGraph(attributePaths = {"tipo", "servicios", "imagenes"})
    Optional<Publicacion> findById(Long id);

    // Mismo motivo: el listado reporta los campos faltantes, que miran servicios e imágenes.
    @EntityGraph(attributePaths = {"servicios", "imagenes"})
    List<Publicacion> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);
}
