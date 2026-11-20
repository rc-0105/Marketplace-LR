package co.edu.unbosque.marketplace.auditoria.controllers.dto;

import co.edu.unbosque.marketplace.auditoria.services.FiltroAuditoria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Parámetros de la lista de auditoría; todos son opcionales (REQ-CA2, CA3, CA4). */
public record ConsultaAuditoriaRequest(
        String usuario,
        String evento,
        String entidad,
        String entidadId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate desde,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate hasta,

        @PositiveOrZero(message = "La página no puede ser negativa")
        Integer pagina,

        @Min(value = 1, message = "El tamaño debe ser al menos 1")
        @Max(value = 200, message = "El tamaño no puede superar 200")
        Integer tamano) {

    public static final int TAMANO_POR_DEFECTO = 50;

    public ConsultaAuditoriaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamano = tamano == null ? TAMANO_POR_DEFECTO : tamano;
    }

    public FiltroAuditoria filtro() {
        return new FiltroAuditoria(usuario, evento, entidad, entidadId, desde, hasta);
    }
}
