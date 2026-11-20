package co.edu.unbosque.marketplace.auditoria.controllers;

import co.edu.unbosque.marketplace.auditoria.controllers.dto.ConsultaAuditoriaRequest;
import co.edu.unbosque.marketplace.auditoria.controllers.dto.EventoAuditoriaResponse;
import co.edu.unbosque.marketplace.auditoria.controllers.dto.PaginaResponse;
import co.edu.unbosque.marketplace.auditoria.services.AuditoriaService;
import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de solo lectura del registro de auditoría para el administrador (RF-59, HU-40). */
@RestController
@RequestMapping("/api/v1/admin/auditoria")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /** Eventos de más nuevo a más viejo; los filtros presentes se combinan con AND. */
    @GetMapping
    public PaginaResponse<EventoAuditoriaResponse> listar(@Valid ConsultaAuditoriaRequest peticion) {
        return PaginaResponse.desde(
                auditoriaService.consultar(peticion.filtro(), peticion.pagina(), peticion.tamano()),
                EventoAuditoriaResponse::desde);
    }
}
