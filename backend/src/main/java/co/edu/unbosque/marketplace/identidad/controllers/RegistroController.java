package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.identidad.controllers.dto.RegistrarUsuarioRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.UsuarioResponse;
import co.edu.unbosque.marketplace.identidad.services.RegistroService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro público: no exige rol, es la puerta de entrada (RF-01). */
@RestController
@RequestMapping("/api/v1/auth")
public class RegistroController {

    private final RegistroService registro;

    public RegistroController(RegistroService registro) {
        this.registro = registro;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistrarUsuarioRequest solicitud) {
        return UsuarioResponse.desde(registro.registrar(
                solicitud.nombre(), solicitud.correo(), solicitud.contrasena(), solicitud.rol()));
    }
}
