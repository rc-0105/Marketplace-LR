package co.edu.unbosque.marketplace.compartido.eventos;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evento de dominio que deja rastro en la auditoría. Lo publica un servicio de
 * aplicación cada vez que ocurre una operación crítica (RF-45).
 *
 * @param usuario   quien ejecuta la acción; si no se conoce queda "anonimo"
 * @param accion    categoría de la operación
 * @param entidad   tipo de entidad afectada, por ejemplo "Reserva"
 * @param entidadId identificador de la entidad afectada, si ya existe
 * @param resultado si la operación terminó bien o falló
 * @param fecha     momento en que ocurrió
 * @param datos     contexto adicional; se sanea antes de escribirse
 */
public record EventoCritico(
        String usuario,
        AccionCritica accion,
        String entidad,
        String entidadId,
        ResultadoEvento resultado,
        Instant fecha,
        Map<String, Object> datos) {

    public EventoCritico {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(entidad, "entidad");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(fecha, "fecha");
        usuario = usuario == null || usuario.isBlank() ? "anonimo" : usuario;
        // Map.copyOf rechaza valores nulos, y un dato opcional nulo es válido aquí.
        datos = datos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(datos));
    }
}
