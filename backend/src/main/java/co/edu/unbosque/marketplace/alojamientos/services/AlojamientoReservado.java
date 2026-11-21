package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.ServicioAdicional;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lo que necesita ver de su alojamiento quien ya lo reservó (SOF-35), sin importar el estado actual de la
 * publicación. {@code serviciosAdicionales} son los nombres de los servicios que la publicación ofrece hoy,
 * por id; un servicio que el anfitrión borró ya no está.
 */
public record AlojamientoReservado(Long id, String titulo, String ciudad, String imagenPrincipal,
        Map<Long, String> serviciosAdicionales) {

    static AlojamientoReservado desde(Publicacion publicacion) {
        return new AlojamientoReservado(publicacion.getId(), publicacion.getTitulo(), publicacion.getCiudad(),
                publicacion.getImagenes().stream().findFirst().orElse(null),
                publicacion.getServiciosAdicionales().stream()
                        .collect(Collectors.toUnmodifiableMap(ServicioAdicional::getId, ServicioAdicional::getNombre)));
    }
}
