package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.identidad.controllers.dto.RestringirCuentaRequest;
import co.edu.unbosque.marketplace.identidad.services.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Restricción de cuentas por un administrador (RF-06). */
@RestController
@RequestMapping("/api/v1/admin/usuarios/{id}/restriccion")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class RestriccionCuentaController {

    private final CuentaService cuentas;

    public RestriccionCuentaController(CuentaService cuentas) {
        this.cuentas = cuentas;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restringir(@PathVariable Long id, @Valid @RequestBody RestringirCuentaRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        cuentas.restringir(id, solicitud.motivo(), Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void levantarRestriccion(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        cuentas.levantarRestriccion(id, Long.valueOf(jwt.getSubject()));
    }
}
