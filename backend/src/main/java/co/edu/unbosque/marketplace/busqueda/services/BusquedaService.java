package co.edu.unbosque.marketplace.busqueda.services;

import co.edu.unbosque.marketplace.alojamientos.services.CandidatoBusqueda;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.busqueda.exceptions.BusquedaInvalidaException;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Búsqueda de alojamientos por ciudad y fechas (RF-28, RF-30). Solo orquesta:
 * {@code alojamientos} entrega las publicaciones ACTIVAS de la ciudad y
 * {@code disponibilidad} dice cuáles ofrecen toda la estancia (ADR-2).
 */
@Service
public class BusquedaService {

    private final PublicacionService publicaciones;
    private final DisponibilidadService disponibilidad;

    public BusquedaService(PublicacionService publicaciones, DisponibilidadService disponibilidad) {
        this.publicaciones = publicaciones;
        this.disponibilidad = disponibilidad;
    }

    /** La ciudad y las fechas ya llegan validadas; sin resultados devuelve una lista vacía, no un error. */
    public List<CandidatoBusqueda> buscar(String ciudad, LocalDate llegada, LocalDate salida) {
        if (!salida.isAfter(llegada)) {
            throw new BusquedaInvalidaException("La fecha de salida debe ser posterior a la de llegada");
        }
        List<CandidatoBusqueda> candidatos = publicaciones.buscarCandidatos(ciudad.strip());
        Set<Long> disponibles = disponibilidad.filtrarDisponibles(
                candidatos.stream().map(CandidatoBusqueda::id).collect(Collectors.toSet()), llegada, salida);
        return candidatos.stream().filter(c -> disponibles.contains(c.id())).toList();
    }
}
