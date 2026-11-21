package co.edu.unbosque.marketplace.indicadores.controllers.dto;

import co.edu.unbosque.marketplace.indicadores.services.RangoFechas;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Parámetros del dashboard; las dos fechas son opcionales y se interpretan en hora de Bogotá (RF-42). */
public record ConsultaDashboardRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate desde,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate hasta) {

    public RangoFechas rango() {
        return new RangoFechas(desde, hasta);
    }
}
