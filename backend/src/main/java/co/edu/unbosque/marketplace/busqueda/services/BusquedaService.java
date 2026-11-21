package co.edu.unbosque.marketplace.busqueda.services;

import co.edu.unbosque.marketplace.alojamientos.services.CandidatoBusqueda;
import co.edu.unbosque.marketplace.alojamientos.services.DetalleAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.busqueda.exceptions.BusquedaInvalidaException;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import co.edu.unbosque.marketplace.identidad.services.CuentaService;
import co.edu.unbosque.marketplace.resenas.services.ConsultaResenasService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Búsqueda de alojamientos por ciudad y fechas, con filtros combinados (RF-28, RF-30). Solo orquesta:
 * {@code alojamientos} entrega las publicaciones ACTIVAS de la ciudad,
 * {@code identidad} dice qué anfitriones están restringidos (RF-58) y
 * {@code disponibilidad} dice cuáles ofrecen toda la estancia (ADR-2).
 */
@Service
public class BusquedaService {

    public static final int LIMITE_MAXIMO_DESTACADOS = 60;

    private final PublicacionService publicaciones;
    private final DisponibilidadService disponibilidad;
    private final CuentaService cuentas;
    private final ConsultaResenasService resenas;

    public BusquedaService(PublicacionService publicaciones, DisponibilidadService disponibilidad,
            CuentaService cuentas, ConsultaResenasService resenas) {
        this.publicaciones = publicaciones;
        this.disponibilidad = disponibilidad;
        this.cuentas = cuentas;
        this.resenas = resenas;
    }

    /**
     * La ciudad y las fechas ya llegan validadas; sin resultados devuelve una lista vacía, no un error.
     * El estado de la publicación no cambia al restringir al anfitrión: al rehabilitarlo, sus ACTIVAS vuelven solas.
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
        List<CandidatoBusqueda> activas = publicaciones.buscarCandidatos(ciudad.strip());
        Set<Long> anfitriones = new HashSet<>();
        for (CandidatoBusqueda activa : activas) {
            anfitriones.add(activa.anfitrionId());
        }
        Set<Long> restringidos = cuentas.filtrarRestringidos(anfitriones);

        List<CandidatoBusqueda> candidatos = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (CandidatoBusqueda activa : activas) {
            if (!restringidos.contains(activa.anfitrionId())) {
                candidatos.add(activa);
                ids.add(activa.id());
            }
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

    /**
     * Alojamientos del Inicio público: las ACTIVAS más recientes, sin las de anfitriones restringidos (RF-58).
     * El límite se ajusta al rango permitido en vez de rechazarse: es una vitrina, no una consulta exacta.
     */
    public List<CandidatoBusqueda> destacados(int limite) {
        int tope = Math.min(Math.max(limite, 1), LIMITE_MAXIMO_DESTACADOS);
        List<CandidatoBusqueda> activas = publicaciones.listarActivasRecientes();
        Set<Long> anfitriones = new HashSet<>();
        for (CandidatoBusqueda activa : activas) {
            anfitriones.add(activa.anfitrionId());
        }
        Set<Long> restringidos = cuentas.filtrarRestringidos(anfitriones);

        List<CandidatoBusqueda> resultado = new ArrayList<>();
        for (CandidatoBusqueda activa : activas) {
            if (resultado.size() == tope) {
                break;
            }
            if (!restringidos.contains(activa.anfitrionId())) {
                resultado.add(activa);
            }
        }
        return resultado;
    }

    /**
     * Detalle de una publicación ACTIVA con su calendario y su calificación promedio; si no está activa, 404 y no
     * consulta el calendario ni las reseñas.
     */
    public DetalleBusqueda detalle(Long publicacionId) {
        DetalleAlojamiento alojamiento = publicaciones.consultarActiva(publicacionId);
        return new DetalleBusqueda(alojamiento, disponibilidad.listarLibres(publicacionId),
                resenas.promedio(publicacionId));
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