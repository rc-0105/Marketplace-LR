package co.edu.unbosque.marketplace.alojamientos.controllers;

import co.edu.unbosque.marketplace.alojamientos.controllers.dto.ImagenSubidaResponse;
import co.edu.unbosque.marketplace.alojamientos.services.AlmacenImagenes.ImagenGuardada;
import co.edu.unbosque.marketplace.alojamientos.services.SubidaImagenService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Archivos de imagen de las publicaciones (RF-19): subirlos es del dueño; leerlos es público, porque las
 * fotos las ven los visitantes (la ruta de lectura está abierta a propósito en ConfiguracionSeguridad).
 */
@RestController
@RequestMapping("/api/v1")
public class ImagenController {

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final SubidaImagenService subidas;

    public ImagenController(SubidaImagenService subidas) {
        this.subidas = subidas;
    }

    /** Sube un archivo PNG o JPG; devuelve su URL para agregarla a las imágenes de la publicación. */
    @PostMapping(value = "/publicaciones/{id}/imagenes/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permisos.ANFITRION_O_ADMINISTRADOR)
    public ImagenSubidaResponse subir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo) throws IOException {
        Long usuarioId = Long.valueOf(jwt.getSubject());
        boolean esAdministrador = ROL_ADMINISTRADOR.equals(jwt.getClaimAsString("rol"));
        return new ImagenSubidaResponse(subidas.subir(id, usuarioId, esAdministrador, archivo.getBytes()));
    }

    /** Lectura pública de una imagen subida; el identificador es un UUID, no un nombre de archivo. */
    @GetMapping("/imagenes/{id}")
    public ResponseEntity<byte[]> ver(@PathVariable UUID id) {
        ImagenGuardada imagen = subidas.obtener(id);
        return ResponseEntity.ok()
                .contentType(imagen.tipo().tipoMedio())
                // El id no se reutiliza: el navegador puede guardarla un año.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(imagen.contenido());
    }
}
