package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.infraestructura.RestriccionesPostgres;
import co.edu.unbosque.marketplace.cotizacion.services.Cotizacion;
import co.edu.unbosque.marketplace.cotizacion.services.CotizacionService;
import co.edu.unbosque.marketplace.reservas.exceptions.ModoReservaNoSoportadoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaSuperpuestaException;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmación de una reserva sobre una cotización vigente (SOF-33). Solo cubre la
 * confirmación inmediata: una publicación que exige aprobación del anfitrión se rechaza, ese
 * flujo queda para una historia futura.
 */
@Service
public class ReservaService {

    private static final String ENTIDAD = "Reserva";
    private static final String RESTRICCION_SIN_CRUCE = "reserva_sin_cruce";

    private final ReservaRepository reservas;
    private final CotizacionService cotizaciones;
    private final PublicacionService publicaciones;
    private final PublicadorEventos publicador;

    public ReservaService(ReservaRepository reservas, CotizacionService cotizaciones,
            PublicacionService publicaciones, PublicadorEventos publicador) {
        this.reservas = reservas;
        this.cotizaciones = cotizaciones;
        this.publicaciones = publicaciones;
        this.publicador = publicador;
    }

    /**
     * Si ya existe una reserva no cancelada del mismo huésped con las mismas fechas exactas, la
     * devuelve tal cual (reintento idempotente, ADR-4) sin recotizar ni guardar de nuevo.
     * Si no, vuelve a cotizar para fijar el precio y revalidar que la cotización sigue vigente
     * (AC4) y que la publicación sigue ACTIVA (AC5): {@code cotizar} ya lo exige. Solo el modo
     * {@code INMEDIATA} confirma directo; {@code APROBACION} se rechaza (SOF-33). El cruce de
     * fechas con otra reserva (AC2, AC3) lo decide la restricción de exclusión de PostgreSQL.
     */
    @Transactional
    public Reserva crear(Long publicacionId, Long huespedId, LocalDate llegada, LocalDate salida,
            Set<Long> adicionalesElegidos) {
        Optional<Reserva> existente = reservas.findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(
                huespedId, publicacionId, llegada, salida, EstadoReserva.CANCELADA);
        if (existente.isPresent()) {
            return existente.get();
        }

        Cotizacion cotizacion = cotizaciones.cotizar(publicacionId, llegada, salida, adicionalesElegidos);
        if (!publicaciones.esModoInmediato(publicacionId)) {
            throw new ModoReservaNoSoportadoException();
        }

        Reserva reserva = Reserva.nueva(publicacionId, huespedId, llegada, salida, (int) cotizacion.noches(),
                cotizacion.precioBase(), cotizacion.tarifaLimpieza(), cotizacion.tarifaServicio(),
                cotizacion.descuento(), cotizacion.totalServiciosAdicionales(), cotizacion.total());
        Reserva guardada = guardar(reserva);
        auditar(guardada, huespedId);
        return guardada;
    }

    // El cruce lo decide la restricción de exclusión de PostgreSQL, la única que no deja pasar
    // dos solicitudes simultáneas (ADR-3). El flush la evalúa aquí y no al confirmar.
    private Reserva guardar(Reserva reserva) {
        try {
            return reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException error) {
            if (RestriccionesPostgres.violaExclusion(error, RESTRICCION_SIN_CRUCE)) {
                throw new ReservaSuperpuestaException();
            }
            throw error;
        }
    }

    private void auditar(Reserva reserva, Long huespedId) {
        publicador.publicar(new EventoCritico(String.valueOf(huespedId), AccionCritica.RESERVA, ENTIDAD,
                String.valueOf(reserva.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of("operacion", "crear", "publicacionId", String.valueOf(reserva.getPublicacionId()))));
    }
}
