package co.edu.unbosque.marketplace.identidad.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.identidad.controllers.dto.RestringirCuentaRequest;
import co.edu.unbosque.marketplace.identidad.controllers.dto.UsuarioAdministracionResponse;
import co.edu.unbosque.marketplace.identidad.services.CuentaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El administrador busca cuentas, las restringe y las rehabilita (RF-06, RF-58). */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class AdministracionUsuariosController {

    private final CuentaService cuentas;

    public AdministracionUsuariosController(CuentaService cuentas) {
        this.cuentas = cuentas;
    }

    /** Sin texto devuelve las primeras cuentas por nombre; nunca las eliminadas. */
    @GetMapping
    public List<UsuarioAdministracionResponse> listar(@RequestParam(defaultValue = "") String texto) {
        return cuentas.listarParaAdministracion(texto).stream().map(UsuarioAdministracionResponse::desde).toList();
    }

    @PostMapping("/{id}/restringir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restringir(@PathVariable Long id, @Valid @RequestBody RestringirCuentaRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        cuentas.restringir(id, solicitud.motivo(), Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/{id}/rehabilitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rehabilitar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        cuentas.levantarRestriccion(id, Long.valueOf(jwt.getSubject()));
    }
}
