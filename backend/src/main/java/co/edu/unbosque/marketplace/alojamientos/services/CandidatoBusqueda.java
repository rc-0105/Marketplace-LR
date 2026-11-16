package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import java.math.BigDecimal;
import java.util.Optional;


/** El anfitrión viaja para que la búsqueda descarte a los restringidos; la respuesta de la API no lo expone. */
public record CandidatoBusqueda(Long id, Long anfitrionId, String titulo, String ciudad, String tipo,
        Integer capacidad, BigDecimal precioNoche, String imagenPrincipal) {

    static CandidatoBusqueda desde(Publicacion publicacion) {
        return new CandidatoBusqueda(publicacion.getId(), publicacion.getAnfitrionId(), publicacion.getTitulo(),
                publicacion.getCiudad(),
                Optional.ofNullable(publicacion.getTipo()).map(tipo -> tipo.getNombre()).orElse(null),
                publicacion.getCapacidad(), publicacion.getPrecioNoche(),
                publicacion.getImagenes().stream().findFirst().orElse(null));
    }
}
