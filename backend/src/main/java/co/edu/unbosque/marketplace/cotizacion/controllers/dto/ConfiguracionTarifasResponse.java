package co.edu.unbosque.marketplace.cotizacion.controllers.dto;

import co.edu.unbosque.marketplace.cotizacion.model.ConfiguracionCotizacion;
import java.math.BigDecimal;

public record ConfiguracionTarifasResponse(BigDecimal porcentajeServicio, BigDecimal porcentajeDescuento) {

    public static ConfiguracionTarifasResponse desde(ConfiguracionCotizacion configuracion) {
        return new ConfiguracionTarifasResponse(configuracion.getPorcentajeServicio(),
                configuracion.getPorcentajeDescuento());
    }
}
