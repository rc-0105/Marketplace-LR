package co.edu.unbosque.marketplace.reservas.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

public record CalculoCancelacion(String politica, BigDecimal base, int porcentaje, BigDecimal valorDevolucion) {
    static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private enum Estrategia {
        FLEXIBLE, MODERADA, ESTRICTA, POR_TERMINOS,
        TOTAL {
            @Override int porcentaje(Duration anticipacion, Integer dias, Integer tardio) { return 100; }
        };

        // Se comparten los límites inclusivos; los valores son los aceptados al reservar.
        int porcentaje(Duration anticipacion, Integer dias, Integer tardio) {
            return anticipacion.compareTo(Duration.ofDays(dias)) >= 0 ? 100 : tardio;
        }
    }

    private static final Map<String, Estrategia> POR_POLITICA = Map.of("Flexible", Estrategia.FLEXIBLE,
            "Moderada", Estrategia.MODERADA, "Estricta", Estrategia.ESTRICTA);

    static CalculoCancelacion calcular(String nombre, Integer dias, Integer tardio, LocalDate llegada,
            Instant ahora, BigDecimal base, boolean anfitrion) {
        Estrategia estrategia = anfitrion || nombre == null ? Estrategia.TOTAL : POR_POLITICA.getOrDefault(nombre, Estrategia.POR_TERMINOS);
        int porcentaje = estrategia.porcentaje(Duration.between(ahora, llegada.atStartOfDay(ZONA).toInstant()), dias, tardio);
        var importe = base.multiply(BigDecimal.valueOf(porcentaje)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        return new CalculoCancelacion(nombre, base, porcentaje, importe);
    }
}
