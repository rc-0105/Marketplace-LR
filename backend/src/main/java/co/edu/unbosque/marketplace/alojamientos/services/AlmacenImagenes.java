package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.TipoImagen;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto donde se guardan los archivos de imagen de las publicaciones. Hoy los guarda la base de datos; si
 * algún día pasan a un servicio externo, solo cambia la implementación: la publicación conserva la misma URL.
 */
public interface AlmacenImagenes {

    /** Ruta pública con que se lee una imagen guardada. */
    String RUTA_PUBLICA = "/api/v1/imagenes/";

    /** Guarda el archivo, ya validado, y devuelve su URL pública. */
    String guardar(Long publicacionId, byte[] contenido, TipoImagen tipo);

    Optional<ImagenGuardada> leer(UUID id);

    /** Un archivo guardado, listo para servirse. */
    record ImagenGuardada(TipoImagen tipo, byte[] contenido) {
    }
}
