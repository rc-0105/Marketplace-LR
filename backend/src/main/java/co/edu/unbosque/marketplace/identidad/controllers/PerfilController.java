package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginResponse;
import co.edu.unbosque.marketplace.identidad.services.AutenticacionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones del dueño sobre su propia cuenta (RF-06).
 *
 * <p>Sin {@code @PreAuthorize} a nivel de clase, solo en cada método: {@code reactivar} es público
 * (quien reactiva no tiene sesión) y una anotación de clase con {@code @EnableMethodSecurity} lo
 * bloquearía para los anónimos. Los endpoints que exijan sesión declaran su propio
 * {@code @PreAuthorize(Permisos.AUTENTICADO)}.
 */
@RestController
@RequestMapping("/api/v1/perfil")
public class PerfilController {

    private final AutenticacionService autenticacion;

    public PerfilController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/reactivar")
    public LoginResponse reactivar(@Valid @RequestBody LoginRequest solicitud) {
        return LoginResponse.desde(autenticacion.reactivarCuenta(solicitud.correo(), solicitud.contrasena()));
    }
}
