package co.edu.unbosque.marketplace.reservas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CancelacionResponse;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CancelarReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CrearReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.PagarReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ReservaResponse;
import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.services.CancelacionReservaService;
import co.edu.unbosque.marketplace.reservas.services.PagoReservaService;
import co.edu.unbosque.marketplace.reservas.services.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Confirmación de una reserva sobre una cotización vigente (SOF-33) y su pago (SOF-34) para el huésped. */
@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize(Permisos.HUESPED)
public class ReservaController {

    private final ReservaService reservaService;
    private final PagoReservaService pagoService;
    private final CancelacionReservaService cancelacionService;

    public ReservaController(ReservaService reservaService, PagoReservaService pagoService,
            CancelacionReservaService cancelacionService) {
        this.reservaService = reservaService;
        this.pagoService = pagoService;
        this.cancelacionService = cancelacionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearReservaRequest data) {
        // El huésped sale del token, nunca del cuerpo: nadie reserva a nombre de otro.
        Long huespedId = Long.valueOf(jwt.getSubject());
        return ReservaResponse.desde(reservaService.crear(data.publicacionId(), huespedId, data.llegada(),
                data.salida(), data.serviciosAdicionalesIds()));
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
