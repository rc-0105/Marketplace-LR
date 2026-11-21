package co.edu.unbosque.marketplace.reservas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CrearReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.PagarReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ReservaResponse;
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

    public ReservaController(ReservaService reservaService, PagoReservaService pagoService) {
        this.reservaService = reservaService;
        this.pagoService = pagoService;
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
}
