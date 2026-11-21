package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import co.edu.unbosque.marketplace.alojamientos.model.ImagenArchivo;
import co.edu.unbosque.marketplace.alojamientos.model.TipoImagen;
import co.edu.unbosque.marketplace.alojamientos.repository.ImagenArchivoRepository;
import co.edu.unbosque.marketplace.alojamientos.services.AlmacenImagenes;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Guarda las imágenes en PostgreSQL: persisten entre despliegues, a diferencia del disco del contenedor. */
@Component
public class AlmacenImagenesEnBaseDeDatos implements AlmacenImagenes {

    private final ImagenArchivoRepository imagenes;

    public AlmacenImagenesEnBaseDeDatos(ImagenArchivoRepository imagenes) {
        this.imagenes = imagenes;
    }

    @Override
    @Transactional
    public String guardar(Long publicacionId, byte[] contenido, TipoImagen tipo) {
        UUID id = UUID.randomUUID();
        imagenes.save(new ImagenArchivo(id, publicacionId, tipo, contenido));
        return RUTA_PUBLICA + id;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImagenGuardada> leer(UUID id) {
        Optional<ImagenArchivo> encontrada = imagenes.findById(id);
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ImagenGuardada(encontrada.get().getTipo(), encontrada.get().getContenido()));
    }
}
