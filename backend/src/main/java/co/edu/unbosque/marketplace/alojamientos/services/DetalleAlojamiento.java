package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Lo que un huésped ve de una publicación ACTIVA; no incluye datos internos como el anfitrión o el estado. */
public record DetalleAlojamiento(Long id, String titulo, String descripcion, String ciudad, String tipo,
        Integer capacidad, BigDecimal precioNoche, String reglas, List<String> imagenes, List<String> servicios,
        String politicaCancelacion, String descripcionPolitica) {

    static DetalleAlojamiento desde(Publicacion publicacion) {
        List<String> nombresServicios = new ArrayList<>();
        for (Servicio servicio : publicacion.getServicios()) {
            nombresServicios.add(servicio.getNombre());
        }
        String tipo = null;
        if (publicacion.getTipo() != null) {
            tipo = publicacion.getTipo().getNombre();
        }
        String politica = null;
        String descripcionPolitica = null;
        PoliticaCancelacion politicaCancelacion = publicacion.getPoliticaCancelacion();
        if (politicaCancelacion != null) {
            politica = politicaCancelacion.getNombre();
            descripcionPolitica = politicaCancelacion.getDescripcion();
        }
        return new DetalleAlojamiento(publicacion.getId(), publicacion.getTitulo(), publicacion.getDescripcion(),
                publicacion.getCiudad(), tipo, publicacion.getCapacidad(), publicacion.getPrecioNoche(),
                publicacion.getReglas(), publicacion.getImagenes(), nombresServicios, politica,
                descripcionPolitica);
    }
}
