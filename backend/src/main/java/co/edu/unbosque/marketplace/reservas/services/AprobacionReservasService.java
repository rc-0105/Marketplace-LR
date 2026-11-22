package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.identidad.services.DirectorioUsuariosService;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ResolucionInvalidaException;
import co.edu.unbosque.marketplace.reservas.exceptions.SolicitudNoRespondibleException;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respuesta del anfitrión a las solicitudes de reserva de sus publicaciones (SOF-79, HU-34, RF-52): listar las
 * pendientes, aprobarlas (pasan a PENDIENTE con un apartado de pago nuevo; el huésped paga por el flujo de siempre
 * y queda CONFIRMADA), rechazarlas con motivo, y vencer las que el anfitrión no respondió a tiempo.
 *
 * <p>Cada transición es una actualización condicional de {@link RegistroReservas} (0 filas = perdió la carrera con
 * otra respuesta, con el vencimiento o con la cancelación del huésped, y se responde 409): así exactamente una gana
 * y solo quien gana audita y avisa. No es transaccional salvo el listado, igual que {@link CancelacionReservaService}.
 */
@Service
public class AprobacionReservasService {

    private static final Logger LOG = LoggerFactory.getLogger(AprobacionReservasService.class);
    private static final String ENTIDAD = "Reserva";
    private static final int MOTIVO_MAXIMO = 500;

    private final ReservaRepository reservas;
    private final PublicacionService publicaciones;
    private final DirectorioUsuariosService usuarios;
    private final RegistroReservas registro;
    private final PublicadorEventos publicador;
    private final NotificacionesReserva notificaciones;
    private final Clock reloj;
    private final Duration plazoDePago;

    public AprobacionReservasService(ReservaRepository reservas, PublicacionService publicaciones,
            DirectorioUsuariosService usuarios, RegistroReservas registro, PublicadorEventos publicador,
            NotificacionesReserva notificaciones, Clock reloj,
            @Value("${marketplace.reservas.horas-pago-aprobada:24}") long horasPagoAprobada) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.usuarios = usuarios;
        this.registro = registro;
        this.publicador = publicador;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
        this.plazoDePago = Duration.ofHours(horasPagoAprobada);
    }

    /**
     * Las solicitudes vigentes (PENDIENTE_APROBACION, ventana sin agotar) de las publicaciones del anfitrión, la que
     * vence primero arriba. La propiedad sale de {@link PublicacionService}, nunca de su repositorio.
     */
    @Transactional(readOnly = true)
    public List<ReservaDeAnfitrion> listarSolicitudes(Long anfitrionId) {
        Set<Long> publicacionIds = publicaciones.listarDelAnfitrion(anfitrionId).stream().map(Publicacion::getId)
                .collect(Collectors.toUnmodifiableSet());
        if (publicacionIds.isEmpty()) {
            return List.of();
        }
        List<Reserva> pendientes = reservas.findSolicitudesVigentes(publicacionIds, Instant.now(reloj));
        Map<Long, AlojamientoReservado> alojamientos = publicaciones.consultarReservados(publicacionIds);
        Map<Long, HuespedBasico> huespedes = usuarios
                .consultarBasicos(pendientes.stream().map(Reserva::getHuespedId).collect(Collectors.toSet()));
        return pendientes.stream()
                .map(reserva -> new ReservaDeAnfitrion(reserva, reserva.getEstado(),
                        alojamientos.get(reserva.getPublicacionId()), huespedes.get(reserva.getHuespedId())))
                .toList();
    }

    /**
     * Aprueba la solicitud: 404 si no existe, 403 si la publicación no es de {@code anfitrionId}, 409 si ya no espera
     * respuesta, si venció, si perdió la carrera o si un administrador bloqueó la publicación (SOF-83).
     */
    public ResolucionSolicitud aprobar(Long reservaId, Long anfitrionId) {
        Reserva reserva = solicitudDelAnfitrion(reservaId, anfitrionId, "aprobar_solicitud");
        if (publicaciones.estaBloqueada(reserva.getPublicacionId())) {
            throw new SolicitudNoRespondibleException(
                    "No se puede aprobar la solicitud: un administrador bloqueó el alojamiento.");
        }
        Instant venceEn = Instant.now(reloj).plus(plazoDePago);
        if (!registro.aprobarSolicitud(reservaId, venceEn)) {
            throw perdioLaCarrera();
        }
        auditar(reservaId, anfitrionId, "aprobar_solicitud", Map.of("venceEnPago", venceEn.toString()));
        avisar(reserva, TipoNotificacion.SOLICITUD_APROBADA, null);
        return new ResolucionSolicitud(reservaId, EstadoReserva.PENDIENTE, venceEn, null);
    }

    /**
     * Rechaza la solicitud con un motivo obligatorio (se recorta; máximo 500) y libera las noches. Mismos 404, 403 y
     * 409 que {@link #aprobar}; un motivo vacío o demasiado largo responde 400.
     */
    public ResolucionSolicitud rechazar(Long reservaId, Long anfitrionId, String motivo) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new ResolucionInvalidaException("motivo", "Indica el motivo del rechazo");
        }
        if (limpio.length() > MOTIVO_MAXIMO) {
            throw new ResolucionInvalidaException("motivo", "El motivo no puede superar los 500 caracteres");
        }
        Reserva reserva = solicitudDelAnfitrion(reservaId, anfitrionId, "rechazar_solicitud");
        if (!registro.rechazarSolicitud(reservaId, limpio)) {
            throw perdioLaCarrera();
        }
        auditar(reservaId, anfitrionId, "rechazar_solicitud", Map.of("motivo", limpio));
        avisar(reserva, TipoNotificacion.SOLICITUD_RECHAZADA, limpio);
        return new ResolucionSolicitud(reservaId, EstadoReserva.RECHAZADA, null, limpio);
    }

    /**
     * Vence, fila por fila, las solicitudes cuya ventana se agotó y avisa al huésped (SOLICITUD_VENCIDA) solo cuando
     * esta corrida fue la que cambió la fila: una aprobación o rechazo concurrente, u otra corrida, ya la resolvió.
     * Devuelve cuántas venció. Cada minuto, como el vencimiento de los apartados de las PENDIENTE.
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public int vencerSolicitudesCaducadas() {
        int vencidas = 0;
        for (Reserva candidata : reservas.findByEstadoAndVenceEnLessThanEqual(EstadoReserva.PENDIENTE_APROBACION,
                Instant.now(reloj))) {
            if (registro.vencerSolicitud(candidata.getId())) {
                vencidas++;
                avisar(candidata, TipoNotificacion.SOLICITUD_VENCIDA, null);
            }
        }
        if (vencidas > 0) {
            LOG.info("Se vencieron {} solicitud(es) de reserva sin respuesta del anfitrión", vencidas);
        }
        return vencidas;
    }

    // 404 si no existe; 403 (auditado como fallo) si no es del anfitrión; 409 si ya no espera respuesta.
    private Reserva solicitudDelAnfitrion(Long reservaId, Long anfitrionId, String operacion) {
        Reserva reserva = reservas.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        boolean esDelAnfitrion = publicaciones.consultarAcceso(reserva.getPublicacionId())
                .map(acceso -> acceso.esDe(anfitrionId)).orElse(false);
        if (!esDelAnfitrion) {
            publicar(reservaId, anfitrionId, operacion, ResultadoEvento.FALLO,
                    Map.of("resultado", "intento_no_autorizado"));
            throw new AccesoDenegadoException();
        }
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_APROBACION) {
            throw new SolicitudNoRespondibleException("Esta reserva no está pendiente de aprobación.");
        }
        return reserva;
    }

    private static SolicitudNoRespondibleException perdioLaCarrera() {
        return new SolicitudNoRespondibleException(
                "Esta solicitud ya no está pendiente de aprobación: venció o ya fue respondida.");
    }

    // El aviso es secundario a la transición ya guardada: nunca la deshace.
    private void avisar(Reserva reserva, TipoNotificacion tipo, String motivo) {
        try {
            notificaciones.notificarHuesped(reserva, tipo, motivo);
        } catch (RuntimeException e) {
            LOG.error("No se pudo avisar al huésped de la reserva {}: tipo={}", reserva.getId(), tipo, e);
        }
    }

    private void auditar(Long reservaId, Long anfitrionId, String operacion, Map<String, Object> datos) {
        publicar(reservaId, anfitrionId, operacion, ResultadoEvento.EXITO, datos);
    }

    private void publicar(Long reservaId, Long anfitrionId, String operacion, ResultadoEvento resultado,
            Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", operacion);
        publicador.publicar(new EventoCritico(String.valueOf(anfitrionId), AccionCritica.RESERVA, ENTIDAD,
                String.valueOf(reservaId), resultado, Instant.now(reloj), contexto));
    }
}
