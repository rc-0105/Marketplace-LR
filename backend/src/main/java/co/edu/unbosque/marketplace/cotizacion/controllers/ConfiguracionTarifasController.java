package co.edu.unbosque.marketplace.cotizacion.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.cotizacion.controllers.dto.ConfiguracionTarifasRequest;
import co.edu.unbosque.marketplace.cotizacion.controllers.dto.ConfiguracionTarifasResponse;
import co.edu.unbosque.marketplace.cotizacion.services.ConfiguracionTarifasService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tarifa de servicio y descuento de la plataforma (RF-34): solo el administrador los consulta y cambia. */
@RestController
@RequestMapping("/api/v1/admin/configuracion/tarifas")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class ConfiguracionTarifasController {

    private final ConfiguracionTarifasService configuracionTarifasService;

    public ConfiguracionTarifasController(ConfiguracionTarifasService configuracionTarifasService) {
        this.configuracionTarifasService = configuracionTarifasService;
    }

    /** Los porcentajes con que se calcula hoy una cotización. */
    @GetMapping
    public ConfiguracionTarifasResponse consultar() {
        return ConfiguracionTarifasResponse.desde(configuracionTarifasService.consultar());
    }

    /** Reemplaza ambos porcentajes; un valor negativo o mayor a 100 responde 400. */
    @PutMapping
    public ConfiguracionTarifasResponse actualizar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfiguracionTarifasRequest data) {
        Long administradorId = Long.valueOf(jwt.getSubject());
        return ConfiguracionTarifasResponse.desde(configuracionTarifasService.actualizar(administradorId,
                data.porcentajeServicio(), data.porcentajeDescuento()));
    }
}
