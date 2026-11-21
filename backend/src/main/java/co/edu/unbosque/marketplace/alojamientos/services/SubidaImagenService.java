package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.exceptions.ImagenNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.ImagenNoValidaException;
import co.edu.unbosque.marketplace.alojamientos.model.TipoImagen;
import co.edu.unbosque.marketplace.alojamientos.services.AlmacenImagenes.ImagenGuardada;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Subida de archivos de imagen de una publicación (RF-19). Solo el dueño o un administrador sube; el archivo
 * debe ser PNG o JPEG por su contenido y pesar a lo sumo 5 MB. La URL que devuelve se guarda después con las
 * demás imágenes de la publicación, que es lo que fija su orden.
 */
@Service
public class SubidaImagenService {

    public static final int TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024;

    private final PublicacionService publicaciones;
    private final AlmacenImagenes almacen;

    public SubidaImagenService(PublicacionService publicaciones, AlmacenImagenes almacen) {
        this.publicaciones = publicaciones;
        this.almacen = almacen;
    }

    /** Valida el archivo y lo guarda; devuelve su URL pública. */
    public String subir(Long publicacionId, Long usuarioId, boolean esAdministrador, byte[] contenido) {
        // Primero el acceso: a quien no es dueño no se le revela nada del archivo.
        publicaciones.consultar(publicacionId, usuarioId, esAdministrador);
        if (contenido == null || contenido.length == 0) {
            throw new ImagenNoValidaException("El archivo está vacío.");
        }
        if (contenido.length > TAMANO_MAXIMO_BYTES) {
            throw new ImagenNoValidaException("La imagen pesa más de 5 MB.");
        }
        TipoImagen tipo = TipoImagen.detectar(contenido);
        if (tipo == null) {
            throw new ImagenNoValidaException("Solo se admiten imágenes PNG o JPG.");
        }
        return almacen.guardar(publicacionId, contenido, tipo);
    }

    /** Lectura pública: una imagen publicada la ven todos los visitantes. */
    public ImagenGuardada obtener(UUID id) {
        Optional<ImagenGuardada> imagen = almacen.leer(id);
        if (imagen.isEmpty()) {
            throw new ImagenNoEncontradaException();
        }
        return imagen.get();
    }
}
