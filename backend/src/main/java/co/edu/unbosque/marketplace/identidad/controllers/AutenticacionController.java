package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginResponse;
import co.edu.unbosque.marketplace.identidad.services.AutenticacionService;
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

/** Inicio de sesión público (RF-02) y cierre de sesión de quien ya está autenticado (RF-03). */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final AutenticacionService autenticacion;

    public AutenticacionController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/login")
    public LoginResponse iniciarSesion(@Valid @RequestBody LoginRequest solicitud) {
        return LoginResponse.desde(autenticacion.iniciarSesion(solicitud.correo(), solicitud.contrasena()));
    }

    @PostMapping("/logout")
    @PreAuthorize(Permisos.AUTENTICADO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarSesion(@AuthenticationPrincipal Jwt jwt) {
        autenticacion.cerrarSesion(jwt.getId(), Long.valueOf(jwt.getSubject()), jwt.getExpiresAt());
    }
}
