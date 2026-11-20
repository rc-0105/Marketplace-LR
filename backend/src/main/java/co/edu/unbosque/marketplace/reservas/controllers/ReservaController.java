package co.edu.unbosque.marketplace.reservas.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.reservas.controllers.dto.CrearReservaRequest;
import co.edu.unbosque.marketplace.reservas.controllers.dto.ReservaResponse;
import co.edu.unbosque.marketplace.reservas.services.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Confirmación de una reserva sobre una cotización vigente para el huésped (SOF-33). */
@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize(Permisos.HUESPED)
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearReservaRequest data) {
        // El huésped sale del token, nunca del cuerpo: nadie reserva a nombre de otro.
        Long huespedId = Long.valueOf(jwt.getSubject());
        return ReservaResponse.desde(reservaService.crear(data.publicacionId(), huespedId, data.llegada(),
                data.salida(), data.serviciosAdicionalesIds()));
    }
}
