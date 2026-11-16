package co.edu.unbosque.marketplace.busqueda.services;

import co.edu.unbosque.marketplace.alojamientos.services.CandidatoBusqueda;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.busqueda.exceptions.BusquedaInvalidaException;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import co.edu.unbosque.marketplace.identidad.services.CuentaService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Búsqueda de alojamientos por ciudad y fechas (RF-28, RF-30). Solo orquesta:
 * {@code alojamientos} entrega las publicaciones ACTIVAS de la ciudad,
 * {@code identidad} dice qué anfitriones están restringidos (RF-58) y
 * {@code disponibilidad} dice cuáles ofrecen toda la estancia (ADR-2).
 */
@Service
public class BusquedaService {

    private final PublicacionService publicaciones;
    private final DisponibilidadService disponibilidad;
    private final CuentaService cuentas;

    public BusquedaService(PublicacionService publicaciones, DisponibilidadService disponibilidad,
            CuentaService cuentas) {
        this.publicaciones = publicaciones;
        this.disponibilidad = disponibilidad;
        this.cuentas = cuentas;
    }

    /**
     * La ciudad y las fechas ya llegan validadas; sin resultados devuelve una lista vacía, no un error.
     * El estado de la publicación no cambia al restringir al anfitrión: al rehabilitarlo, sus ACTIVAS vuelven solas.
     */
    public List<CandidatoBusqueda> buscar(String ciudad, LocalDate llegada, LocalDate salida) {
        if (!salida.isAfter(llegada)) {
            throw new BusquedaInvalidaException("La fecha de salida debe ser posterior a la de llegada");
        }
        List<CandidatoBusqueda> activas = publicaciones.buscarCandidatos(ciudad.strip());
        Set<Long> restringidos = cuentas.filtrarRestringidos(
                activas.stream().map(CandidatoBusqueda::anfitrionId).collect(Collectors.toSet()));
        List<CandidatoBusqueda> candidatos = activas.stream()
                .filter(c -> !restringidos.contains(c.anfitrionId())).toList();
        Set<Long> disponibles = disponibilidad.filtrarDisponibles(
                candidatos.stream().map(CandidatoBusqueda::id).collect(Collectors.toSet()), llegada, salida);
        return candidatos.stream().filter(c -> disponibles.contains(c.id())).toList();
    }
}
