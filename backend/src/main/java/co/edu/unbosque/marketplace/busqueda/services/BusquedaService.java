package co.edu.unbosque.marketplace.busqueda.services;

import co.edu.unbosque.marketplace.alojamientos.services.CandidatoBusqueda;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.busqueda.exceptions.BusquedaInvalidaException;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Búsqueda de alojamientos por ciudad y fechas, con filtros combinados (RF-28, RF-29, RF-30). Solo orquesta:
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

    /**
     * La ciudad y las fechas ya llegan validadas; sin resultados devuelve una lista vacía, no un error.
     * Los filtros se combinan: un resultado debe cumplir todos los que vengan informados.
     */
    public List<CandidatoBusqueda> buscar(String ciudad, LocalDate llegada, LocalDate salida,
            FiltrosBusqueda filtros) {
        if (!salida.isAfter(llegada)) {
            throw new BusquedaInvalidaException("La fecha de salida debe ser posterior a la de llegada");
        }
        if (filtros.precioMin() != null && filtros.precioMax() != null
                && filtros.precioMin().compareTo(filtros.precioMax()) > 0) {
            throw new BusquedaInvalidaException("El precio mínimo no puede ser mayor que el máximo");
        }
        List<CandidatoBusqueda> candidatos = publicaciones.buscarCandidatos(ciudad.strip());
        Set<Long> ids = new HashSet<>();
        for (CandidatoBusqueda candidato : candidatos) {
            ids.add(candidato.id());
        }
        Set<Long> disponibles = disponibilidad.filtrarDisponibles(ids, llegada, salida);

        List<CandidatoBusqueda> resultado = new ArrayList<>();
        for (CandidatoBusqueda candidato : candidatos) {
            if (disponibles.contains(candidato.id()) && cumpleFiltros(candidato, filtros)) {
                resultado.add(candidato);
            }
        }
        return resultado;
    }

    private boolean cumpleFiltros(CandidatoBusqueda candidato, FiltrosBusqueda filtros) {
        if (filtros.capacidad() != null
                && (candidato.capacidad() == null || candidato.capacidad() < filtros.capacidad())) {
            return false;
        }
        if (filtros.tipo() != null && !filtros.tipo().isBlank()
                && !filtros.tipo().strip().equalsIgnoreCase(candidato.tipo())) {
            return false;
        }
        if (filtros.precioMin() != null
                && (candidato.precioNoche() == null || candidato.precioNoche().compareTo(filtros.precioMin()) < 0)) {
            return false;
        }
        if (filtros.precioMax() != null
                && (candidato.precioNoche() == null || candidato.precioNoche().compareTo(filtros.precioMax()) > 0)) {
            return false;
        }
        return filtros.servicios() == null || candidato.servicios().containsAll(filtros.servicios());
    }
}
