package co.edu.unbosque.marketplace.alojamientos.repository;

import co.edu.unbosque.marketplace.alojamientos.model.ImagenArchivo;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenArchivoRepository extends JpaRepository<ImagenArchivo, UUID> {
}
