package co.edu.unbosque.marketplace.reservas.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Decisión del administrador sobre una reserva en revisión manual (SOF-83). El motivo es obligatorio para
 * CANCELAR; lo exige el servicio, que responde el mismo mapa {@code errores}.
 */
public record ResolverRevisionRequest(
        @NotBlank(message = "La decisión es obligatoria.")
        @Pattern(regexp = "CONFIRMAR|CANCELAR", message = "La decisión debe ser CONFIRMAR o CANCELAR.")
        String decision,
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
        String motivo) {
}
