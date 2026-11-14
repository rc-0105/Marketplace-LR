package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


public record CandidatoBusqueda(Long id, String titulo, String ciudad, String tipo, Integer capacidad,
        BigDecimal precioNoche, String imagenPrincipal, Set<Long> servicios) {

    static CandidatoBusqueda desde(Publicacion publicacion) {
        Set<Long> idsServicios = new HashSet<>();
        for (Servicio servicio : publicacion.getServicios()) {
            idsServicios.add(servicio.getId());
        }
        return new CandidatoBusqueda(publicacion.getId(), publicacion.getTitulo(), publicacion.getCiudad(),
                Optional.ofNullable(publicacion.getTipo()).map(tipo -> tipo.getNombre()).orElse(null),
                publicacion.getCapacidad(), publicacion.getPrecioNoche(),
                publicacion.getImagenes().stream().findFirst().orElse(null), idsServicios);
    }
}
