package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    // open-in-view está apagado: la respuesta se arma fuera de la transacción,
    // así que los servicios y las imágenes se traen en la misma consulta.
    @Override
    @EntityGraph(attributePaths = {"servicios", "imagenes"})
    Optional<Publicacion> findById(Long id);
}
