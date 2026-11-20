package co.edu.unbosque.marketplace.disponibilidad.model;

import java.time.LocalDate;

/**
 * Respuesta de la regla de disponibilidad para la estancia {@code [desde, hasta)} de una publicación.
 * Solo guarda el motivo: {@link #disponible()} se deriva y no puede contradecirlo.
 *
 * @param motivo por qué no está disponible, o {@code null} si lo está
 */
public record DisponibilidadRango(Long publicacionId, LocalDate desde, LocalDate hasta, MotivoNoDisponible motivo) {

    public boolean disponible() {
        return motivo == null;
    }
}
