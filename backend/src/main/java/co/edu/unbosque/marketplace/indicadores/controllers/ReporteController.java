package co.edu.unbosque.marketplace.indicadores.controllers;

import co.edu.unbosque.marketplace.compartido.seguridad.Permisos;
import co.edu.unbosque.marketplace.indicadores.controllers.dto.ConsultaDashboardRequest;
import co.edu.unbosque.marketplace.indicadores.controllers.dto.ReporteResponse;
import co.edu.unbosque.marketplace.indicadores.services.ArchivoReporte;
import co.edu.unbosque.marketplace.indicadores.services.FormatoExportacion;
import co.edu.unbosque.marketplace.indicadores.services.ReporteService;
import co.edu.unbosque.marketplace.indicadores.services.TipoReporte;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reportes del administrador (RF-43, RF-44, HU-28). Sin {@code formato} responde el reporte en JSON
 * para la pantalla; con {@code formato=pdf|xlsx} descarga el mismo reporte y registra la exportación.
 * Las fechas se filtran igual que en el dashboard.
 */
@RestController
@RequestMapping("/api/v1/admin/reportes")
@PreAuthorize(Permisos.ADMINISTRADOR)
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping(value = "/{tipo}", params = "!formato")
    public ReporteResponse consultar(@PathVariable String tipo, ConsultaDashboardRequest peticion) {
        return ReporteResponse.desde(reporteService.generar(TipoReporte.desdeRuta(tipo), peticion.rango()));
    }

    @GetMapping(value = "/{tipo}", params = "formato")
    public ResponseEntity<byte[]> exportar(@AuthenticationPrincipal Jwt jwt, @PathVariable String tipo,
            @RequestParam String formato, ConsultaDashboardRequest peticion) {
        // Quién exporta sale del token: el registro de auditoría no se puede firmar a nombre de otro.
        ArchivoReporte archivo = reporteService.exportar(TipoReporte.desdeRuta(tipo),
                FormatoExportacion.desdeParametro(formato), peticion.rango(), Long.valueOf(jwt.getSubject()));
        return ResponseEntity.ok()
                .contentType(archivo.formato().tipoContenido())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
