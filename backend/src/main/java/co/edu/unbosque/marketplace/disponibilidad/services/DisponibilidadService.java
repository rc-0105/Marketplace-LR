package co.edu.unbosque.marketplace.disponibilidad.services;

import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.CalendarioNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.FechasPeriodoInvalidasException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoConReservaException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoSuperpuestoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.RangoConsultaInvalidoException;
import co.edu.unbosque.marketplace.disponibilidad.model.DisponibilidadRango;
import co.edu.unbosque.marketplace.disponibilidad.model.MotivoNoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.repository.PeriodoDisponibleRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodos en que el anfitrión ofrece su alojamientos y sus cambios, que una
 * reserva confirmada dentro del periodo impide.
 */
@Service
public class DisponibilidadService {

    private static final String ENTIDAD = "PeriodoDisponible";
    // "Hoy" es el día en Colombia, donde están los alojamientos, no el del servidor.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final String RESTRICCION_SIN_CRUCE = "periodo_disponible_sin_cruce";
    private static final String VIOLACION_EXCLUSION = "23P01";

    private final PeriodoDisponibleRepository periodos;
    private final PublicacionService publicaciones;
    private final ConsultaReservasPeriodo reservas;
    private final ConsultaReservasCalendario ocupacion;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public DisponibilidadService(PeriodoDisponibleRepository periodos, PublicacionService publicaciones,
            ConsultaReservasPeriodo reservas, ConsultaReservasCalendario ocupacion, PublicadorEventos publicador,
            Clock reloj) {
        this.periodos = periodos;
        this.publicaciones = publicaciones;
        this.reservas = reservas;
        this.ocupacion = ocupacion;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Transactional
    public PeriodoDisponible registrar(Long publicacionId, Long anfitrionId, LocalDate inicio, LocalDate fin) {
        publicaciones.exigirPropietario(publicacionId, anfitrionId);
        validarFechas(inicio, fin, null);
        PeriodoDisponible periodo = guardar(PeriodoDisponible.nuevo(publicacionId, inicio, fin));
        auditar(periodo, anfitrionId, "registrar_periodo");
        return periodo;
    }

    /** Periodos de la publicación del anfitrión, del más próximo al más lejano. */
    @Transactional(readOnly = true)
    public List<PeriodoDisponible> listar(Long publicacionId, Long anfitrionId) {
        publicaciones.exigirPropietario(publicacionId, anfitrionId);
        return periodos.findByPublicacionIdOrderByFechaInicioAsc(publicacionId);
    }

    /** Calendario público de la publicación, del periodo más próximo al más lejano. */
    @Transactional(readOnly = true)
    public List<PeriodoLibre> listarLibres(Long publicacionId) {
        List<PeriodoLibre> libres = new ArrayList<>();
        for (PeriodoDisponible periodo : periodos.findByPublicacionIdOrderByFechaInicioAsc(publicacionId)) {
            libres.add(PeriodoLibre.desde(periodo));
        }
        return libres;
    }

    /**
     * De las publicaciones dadas, las que ofrecen toda la estancia {@code [llegada, salida)}: un periodo la
     * contiene y ninguna reserva que ocupa noches la cruza. Es el único punto donde la búsqueda pregunta por
     * disponibilidad, y es informativo: la garantía contra el doble reservado la da la base de datos (ADR-3).
     */
    @Transactional(readOnly = true)
    public Set<Long> filtrarDisponibles(Collection<Long> publicacionIds, LocalDate llegada, LocalDate salida) {
        Set<Long> noDisponibles = evaluar(publicacionIds, llegada, salida).keySet();
        return publicacionIds.stream().filter(id -> !noDisponibles.contains(id)).collect(Collectors.toSet());
    }

    /**
     * Si la estancia {@code [desde, hasta)} está disponible y, si no, por qué, con la misma regla de la
     * búsqueda. El dueño consulta su publicación en cualquier estado; otro usuario, solo si es ACTIVA, y
     * una inexistente o no visible responde lo mismo que el calendario. El acceso va antes que el rango: a
     * quien no puede ver la publicación no se le revela nada más, ni siquiera un 400.
     */
    @Transactional(readOnly = true)
    public DisponibilidadRango consultar(Long publicacionId, Long usuarioId, LocalDate desde, LocalDate hasta) {
        publicaciones.consultarAcceso(publicacionId).filter(a -> a.esDe(usuarioId) || a.publica())
                .orElseThrow(CalendarioNoEncontradoException::new);
        if (!hasta.isAfter(desde)) {
            throw new RangoConsultaInvalidoException("La fecha final debe ser posterior a la fecha inicial.");
        }
        MotivoNoDisponible motivo = evaluar(List.of(publicacionId), desde, hasta).get(publicacionId);
        return new DisponibilidadRango(publicacionId, desde, hasta, motivo);
    }

    /**
     * Regla única de disponibilidad: por cada publicación NO disponible, el motivo. Una publicación
     * disponible no aparece en el mapa. Sin periodo que contenga la estancia gana FUERA_DE_PERIODO, y al
     * puerto de reservas solo se le pregunta por las que sí tienen periodo.
     */
    private Map<Long, MotivoNoDisponible> evaluar(Collection<Long> publicacionIds, LocalDate desde, LocalDate hasta) {
        if (publicacionIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> conPeriodo = periodos
                .findByPublicacionIdInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(publicacionIds, desde, hasta)
                .stream()
                .map(PeriodoDisponible::getPublicacionId)
                .collect(Collectors.toSet());
        Set<Long> ocupadas = ocupacion.publicacionesOcupadas(conPeriodo, desde, hasta);
        Map<Long, MotivoNoDisponible> motivos = new HashMap<>();
        for (Long id : publicacionIds) {
            if (!conPeriodo.contains(id)) {
                motivos.put(id, MotivoNoDisponible.FUERA_DE_PERIODO);
            } else if (ocupadas.contains(id)) {
                motivos.put(id, MotivoNoDisponible.RESERVADO);
            }
        }
        return motivos;
    }

    @Transactional
    public PeriodoDisponible modificar(Long periodoId, Long anfitrionId, LocalDate inicio, LocalDate fin) {
        PeriodoDisponible periodo = buscarConAcceso(periodoId, anfitrionId);
        exigirSinReserva(periodo);
        validarFechas(inicio, fin, periodo.getFechaInicio());
        periodo.cambiarFechas(inicio, fin);
        PeriodoDisponible guardado = guardar(periodo);
        auditar(guardado, anfitrionId, "modificar_periodo");
        return guardado;
    }

    @Transactional
    public void eliminar(Long periodoId, Long anfitrionId) {
        PeriodoDisponible periodo = buscarConAcceso(periodoId, anfitrionId);
        exigirSinReserva(periodo);
        periodos.delete(periodo);
        auditar(periodo, anfitrionId, "eliminar_periodo");
    }

    // La propiedad va antes que el contenido: a quien no es dueño no se le revela nada más.
    private PeriodoDisponible buscarConAcceso(Long periodoId, Long anfitrionId) {
        PeriodoDisponible periodo = periodos.findById(periodoId).orElseThrow(PeriodoNoEncontradoException::new);
        publicaciones.exigirPropietario(periodo.getPublicacionId(), anfitrionId);
        return periodo;
    }

    // Un periodo que ya empezó conserva su fecha inicial al modificarse: solo se exige
    // que no sea pasada cuando cambia.
    private void validarFechas(LocalDate inicio, LocalDate fin, LocalDate inicioActual) {
        if (!fin.isAfter(inicio)) {
            throw new FechasPeriodoInvalidasException("La fecha final debe ser posterior a la fecha inicial.");
        }
        if (!inicio.equals(inicioActual) && inicio.isBefore(LocalDate.now(reloj.withZone(ZONA)))) {
            throw new FechasPeriodoInvalidasException("La fecha inicial no puede ser anterior a hoy.");
        }
    }

    // El cruce lo decide la restricción de exclusión de PostgreSQL, la única que no deja
    // pasar dos solicitudes simultáneas. El flush la evalúa aquí y no al confirmar.
    private PeriodoDisponible guardar(PeriodoDisponible periodo) {
        try {
            return periodos.saveAndFlush(periodo);
        } catch (DataIntegrityViolationException error) {
            if (violaRestriccion(error, RESTRICCION_SIN_CRUCE)) {
                throw new PeriodoSuperpuestoException();
            }
            throw error;
        }
    }

    // Hibernate no extrae el nombre de una restricción de exclusión (SQLState 23P01), así
    // que se busca en el mensaje del error de PostgreSQL.
    private static boolean violaRestriccion(Throwable error, String restriccion) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && VIOLACION_EXCLUSION.equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains(restriccion)) {
                return true;
            }
        }
        return false;
    }

    private void exigirSinReserva(PeriodoDisponible periodo) {
        reservas.reservaConfirmadaEn(periodo.getPublicacionId(), periodo.getFechaInicio(), periodo.getFechaFin())
                .ifPresent(reservaId -> {
                    throw new PeriodoConReservaException(reservaId);
                });
    }

    private void auditar(PeriodoDisponible periodo, Long anfitrionId, String operacion) {
        publicador.publicar(new EventoCritico(String.valueOf(anfitrionId), AccionCritica.PUBLICACION, ENTIDAD,
                String.valueOf(periodo.getId()), ResultadoEvento.EXITO, Instant.now(reloj),
                Map.of("operacion", operacion, "publicacionId", String.valueOf(periodo.getPublicacionId()))));
    }
}