package co.edu.unbosque.marketplace.indicadores.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Rango de días, ambos incluidos y en hora de Bogotá (RF-42). Un extremo ausente deja el rango
 * abierto por ese lado.
 */
public record RangoFechas(LocalDate desde, LocalDate hasta) {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public boolean esCoherente() {
        return desde == null || hasta == null || !desde.isAfter(hasta);
    }

    /** Primer instante incluido, o {@code null} si el rango no tiene inicio. */
    public Instant inicio() {
        return desde == null ? null : desde.atStartOfDay(ZONA).toInstant();
    }

    /** Primer instante excluido (el inicio del día siguiente a {@code hasta}), o {@code null} si no tiene fin. */
    public Instant fin() {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }
}
