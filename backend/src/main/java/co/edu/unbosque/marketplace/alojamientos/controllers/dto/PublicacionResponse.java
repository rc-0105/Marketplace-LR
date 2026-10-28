package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Vista de una publicación. {@code camposFaltantes} indica qué le falta al
 * borrador para poder activarse; vacío significa que está completo.
 */
public record PublicacionResponse(
        Long id,
        Long anfitrionId,
        String titulo,
        String descripcion,
        String ciudad,
        String direccion,
        String tipo,
        Integer capacidad,
        BigDecimal precioNoche,
        List<String> servicios,
        String reglas,
        List<String> imagenes,
        String estado,
        Instant creadoEn,
        List<String> camposFaltantes) {

    public static PublicacionResponse desde(Publicacion publicacion) {
        return new PublicacionResponse(
                publicacion.getId(),
                publicacion.getAnfitrionId(),
                publicacion.getTitulo(),
                publicacion.getDescripcion(),
                publicacion.getCiudad(),
                publicacion.getDireccion(),
                publicacion.getTipo() == null ? null : publicacion.getTipo().name(),
                publicacion.getCapacidad(),
                publicacion.getPrecioNoche(),
                List.copyOf(publicacion.getServicios()),
                publicacion.getReglas(),
                publicacion.getImagenes(),
                publicacion.getEstado().name(),
                publicacion.getCreadoEn(),
                publicacion.camposFaltantesParaActivar());
    }
}
