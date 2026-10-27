package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginResponse;
import co.edu.unbosque.marketplace.identidad.services.AutenticacionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inicio de sesión público: no exige rol, es la puerta de entrada (RF-02). */
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
}
