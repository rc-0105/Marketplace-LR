package co.edu.unbosque.marketplace.alojamientos.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Datos con que se crea un borrador. Solo el título es obligatorio; lo demás
 * puede faltar y se reporta en {@link Publicacion#camposFaltantesParaActivar()}.
 */
public record DatosBorrador(
        String titulo,
        String descripcion,
        String ciudad,
        String direccion,
        TipoAlojamiento tipo,
        Integer capacidad,
        BigDecimal precioNoche,
        Set<String> servicios,
        String reglas,
        List<String> imagenes) {

    public DatosBorrador {
        Objects.requireNonNull(titulo, "titulo");
        // LinkedHashSet conserva el orden en que el anfitrión escribió los servicios.
        if (servicios == null) {
            servicios = Set.of();
        } else {
            servicios = Collections.unmodifiableSet(new LinkedHashSet<>(servicios));
        }

        if (imagenes == null) {
            imagenes = List.of();
        } else {
            imagenes = List.copyOf(imagenes);
        }
    }
}
