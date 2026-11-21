package co.edu.unbosque.marketplace.reservas.controllers;

import co.edu.unbosque.marketplace.compartido.idempotencia.ServicioIdempotencia;
import co.edu.unbosque.marketplace.compartido.idempotencia.SolicitudIdempotente;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CancelacionResponse;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CancelarReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CrearReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.DetalleReservaResponse;
import co.edu.unbosque.marketplace.reservas.controllers.dto.MisReservasResponse;
import co.edu.unbosque.marketplace.reservas.controllers.dto.PagarReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ReservaResponse;
import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.services.CancelacionReservaService;
import co.edu.unbosque.marketplace.reservas.services.HistorialReservasService;
import co.edu.unbosque.marketplace.reservas.services.PagoReservaService;
import co.edu.unbosque.marketplace.reservas.services.ReservaService;
import jakarta.validation.Valid;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirmación de una reserva sobre una cotización vigente (SOF-33), su pago (SOF-34), su cancelación (SOF-75)
 * y el estado e historial de las reservas (SOF-35) para el huésped.
 */
@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize(Permisos.HUESPED)
public class ReservaController {

    private static final String OPERACION_CREAR = "POST /api/v1/reservas";

    private final ReservaService reservaService;
    private final PagoReservaService pagoService;
    private final CancelacionReservaService cancelacionService;
    private final ServicioIdempotencia idempotencia;
    private final HistorialReservasService historialService;

    public ReservaController(ReservaService reservaService, PagoReservaService pagoService,
            CancelacionReservaService cancelacionService, ServicioIdempotencia idempotencia,
            HistorialReservasService historialService) {
        this.reservaService = reservaService;
        this.pagoService = pagoService;
        this.cancelacionService = cancelacionService;
        this.idempotencia = idempotencia;
        this.historialService = historialService;
    }

    /**
     * Crea la reserva una sola vez por {@code Idempotency-Key} (SOF-77): repetir la misma solicitud con la
     * misma clave devuelve la respuesta guardada, con el mismo código, sin crear otra reserva.
     */
    @PostMapping
    public ResponseEntity<String> crear(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = ServicioIdempotencia.CABECERA, required = false) String clave,
            @Valid @RequestBody CrearReservaRequest data) {
        // El huésped sale del token, nunca del cuerpo: nadie reserva a nombre de otro.
        Long huespedId = Long.valueOf(jwt.getSubject());
        SolicitudIdempotente solicitud = new SolicitudIdempotente(huespedId, clave, OPERACION_CREAR,
                contenidoCanonico(data));
        return idempotencia.ejecutar(solicitud, HttpStatus.CREATED,
                () -> ReservaResponse.desde(reservaService.crear(data.publicacionId(), huespedId, data.llegada(),
                        data.salida(), data.serviciosAdicionalesIds())))
                .comoRespuesta();
    }

    // Mismo texto para solicitudes equivalentes: los servicios adicionales son un conjunto, su orden no cuenta.
    private static String contenidoCanonico(CrearReservaRequest data) {
        String servicios = data.serviciosAdicionalesIds() == null ? ""
                : data.serviciosAdicionalesIds().stream().sorted().map(String::valueOf)
                        .collect(Collectors.joining(","));
        return "publicacion=" + data.publicacionId() + ";llegada=" + data.llegada() + ";salida=" + data.salida()
                + ";servicios=" + servicios;
    }

    /** Las reservas del huésped autenticado, futuras y pasadas, con su estado actual (SOF-35, RF-38). */
    @GetMapping("/mias")
    public MisReservasResponse mias(@AuthenticationPrincipal Jwt jwt) {
        // El huésped sale del token: no hay forma de pedir las reservas de otro.
        return MisReservasResponse.desde(historialService.mias(Long.valueOf(jwt.getSubject())));
    }

    /** Una reserva del huésped autenticado con su alojamiento, desglose y estado (SOF-35, RF-38). */
    @GetMapping("/{id}")
    public DetalleReservaResponse detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return DetalleReservaResponse.desde(historialService.detalle(id, Long.valueOf(jwt.getSubject())));
    }

    /** Paga la reserva PENDIENTE del huésped con la pasarela en modo prueba (SOF-34, RF-37). */
    @PostMapping("/{id}/pago")
    public ReservaResponse pagar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody PagarReservaRequest data) {
        return ReservaResponse.desde(pagoService.pagar(id, Long.valueOf(jwt.getSubject()), data.metodoPago()));
    }

    /**
     * Cancela una reserva CONFIRMADA cuya estancia no ha terminado (SOF-75, HU-30): la cancela su huésped
     * titular o el anfitrión de la publicación. El filtro de seguridad solo mira el rol; que sea uno de los
     * dos dueños posibles lo decide el service.
     */
    @PostMapping("/{id}/cancelacion")
    @PreAuthorize(Permisos.HUESPED_O_ANFITRION)
    public CancelacionResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody CancelarReservaRequest data) {
        Cancelacion cancelacion = cancelacionService.cancelar(id, Long.valueOf(jwt.getSubject()), data.motivo());
        return new CancelacionResponse(cancelacion.getReservaId(), cancelacion.getEstadoFinal().name(),
                cancelacion.getMotivo(), cancelacion.getValorDevolucion(), cancelacion.getFecha());
    }
}
