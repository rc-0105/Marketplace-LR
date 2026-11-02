package co.edu.unbosque.marketplace.disponibilidad.services;

import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.FechasPeriodoInvalidasException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoConReservaException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoSuperpuestoException;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.repository.PeriodoDisponibleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodos en que el anfitrión ofrece su alojamiento (RF-23) y sus cambios, que una
 * reserva confirmada dentro del periodo impide (RF-24).
 */
@Service
public class DisponibilidadService {

    private static final String ENTIDAD = "PeriodoDisponible";
    // "Hoy" es el día en Colombia, donde están los alojamientos, no el del servidor.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final Long SIN_PERIODO = 0L;

    private final PeriodoDisponibleRepository periodos;
    private final PublicacionService publicaciones;
    private final ConsultaReservasPeriodo reservas;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public DisponibilidadService(PeriodoDisponibleRepository periodos, PublicacionService publicaciones,
            ConsultaReservasPeriodo reservas, PublicadorEventos publicador, Clock reloj) {
        this.periodos = periodos;
        this.publicaciones = publicaciones;
        this.reservas = reservas;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Transactional
    public PeriodoDisponible registrar(Long publicacionId, Long anfitrionId, LocalDate inicio, LocalDate fin) {
        publicaciones.exigirPropietario(publicacionId, anfitrionId);
        validarFechas(inicio, fin, null);
        exigirSinCruce(publicacionId, SIN_PERIODO, inicio, fin);
        PeriodoDisponible periodo = periodos.save(PeriodoDisponible.nuevo(publicacionId, inicio, fin));
        auditar(periodo, anfitrionId, "registrar_periodo");
        return periodo;
    }

    /** Periodos de la publicación del anfitrión, del más próximo al más lejano. */
    @Transactional(readOnly = true)
    public List<PeriodoDisponible> listar(Long publicacionId, Long anfitrionId) {
        publicaciones.exigirPropietario(publicacionId, anfitrionId);
        return periodos.findByPublicacionIdOrderByFechaInicioAsc(publicacionId);
    }

    @Transactional
    public PeriodoDisponible modificar(Long periodoId, Long anfitrionId, LocalDate inicio, LocalDate fin) {
        PeriodoDisponible periodo = buscarConAcceso(periodoId, anfitrionId);
        exigirSinReserva(periodo);
        validarFechas(inicio, fin, periodo.getFechaInicio());
        exigirSinCruce(periodo.getPublicacionId(), periodo.getId(), inicio, fin);
        periodo.cambiarFechas(inicio, fin);
        PeriodoDisponible guardado = periodos.save(periodo);
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

    private void exigirSinCruce(Long publicacionId, Long excluirId, LocalDate inicio, LocalDate fin) {
        if (periodos.existeCruce(publicacionId, excluirId, inicio, fin)) {
            throw new PeriodoSuperpuestoException();
        }
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
