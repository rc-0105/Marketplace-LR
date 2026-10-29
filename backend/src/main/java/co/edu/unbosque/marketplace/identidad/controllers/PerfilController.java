package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.identidad.controllers.dto.ConfirmacionCuentaRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.LoginResponse;
import co.edu.unbosque.marketplace.identidad.services.AutenticacionService;
import co.edu.unbosque.marketplace.identidad.services.CuentaService;
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
    private final CuentaService cuentas;

    public PerfilController(AutenticacionService autenticacion, CuentaService cuentas) {
        this.autenticacion = autenticacion;
        this.cuentas = cuentas;
    }

    @PostMapping("/reactivar")
    public LoginResponse reactivar(@Valid @RequestBody LoginRequest solicitud) {
        return LoginResponse.desde(autenticacion.reactivarCuenta(solicitud.correo(), solicitud.contrasena()));
    }

    // El id sale del token, nunca del cuerpo.
    @PostMapping("/desactivar")
    @PreAuthorize(Permisos.AUTENTICADO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@Valid @RequestBody ConfirmacionCuentaRequest solicitud, @AuthenticationPrincipal Jwt jwt) {
        cuentas.desactivar(Long.valueOf(jwt.getSubject()), solicitud.contrasena());
    }

    @PostMapping("/eliminar")
    @PreAuthorize(Permisos.AUTENTICADO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@Valid @RequestBody ConfirmacionCuentaRequest solicitud, @AuthenticationPrincipal Jwt jwt) {
        cuentas.eliminar(Long.valueOf(jwt.getSubject()), solicitud.contrasena());
    }
}
