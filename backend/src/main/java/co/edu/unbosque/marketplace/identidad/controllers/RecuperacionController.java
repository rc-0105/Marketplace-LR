package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.identidad.controllers.dto.ConfirmarRecuperacionRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.RecuperacionSolicitadaResponse;
import co.edu.unbosque.marketplace.identidad.controllers.dto.SolicitarRecuperacionRequest;
import co.edu.unbosque.marketplace.identidad.services.RecuperacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Recuperación pública de la contraseña: quien la usa no puede iniciar sesión (RF-05). */
@RestController
@RequestMapping("/api/v1/auth/recuperacion")
public class RecuperacionController {

    private final RecuperacionService recuperacion;

    public RecuperacionController(RecuperacionService recuperacion) {
        this.recuperacion = recuperacion;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecuperacionSolicitadaResponse solicitar(@Valid @RequestBody SolicitarRecuperacionRequest solicitud) {
        recuperacion.solicitar(solicitud.correo());
        return RecuperacionSolicitadaResponse.GENERICA;
    }

    @PostMapping("/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@Valid @RequestBody ConfirmarRecuperacionRequest solicitud) {
        recuperacion.confirmar(solicitud.correo(), solicitud.codigo(), solicitud.contrasenaNueva());
    }
}
