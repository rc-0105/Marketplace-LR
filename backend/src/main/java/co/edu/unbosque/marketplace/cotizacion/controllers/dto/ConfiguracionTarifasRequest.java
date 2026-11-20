package co.edu.unbosque.marketplace.cotizacion.controllers.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ConfiguracionTarifasRequest(
        @NotNull(message = "Indica el porcentaje de la tarifa de servicio")
        @DecimalMin(value = "0", message = "El porcentaje de la tarifa de servicio no puede ser negativo")
        @DecimalMax(value = "100", message = "El porcentaje de la tarifa de servicio no puede superar el 100 %")
        @Digits(integer = 3, fraction = 2, message = "La tarifa de servicio admite máximo 2 decimales")
        BigDecimal porcentajeServicio,

        @NotNull(message = "Indica el porcentaje de descuento")
        @DecimalMin(value = "0", message = "El porcentaje de descuento no puede ser negativo")
        @DecimalMax(value = "100", message = "El porcentaje de descuento no puede superar el 100 %")
        @Digits(integer = 3, fraction = 2, message = "El descuento admite máximo 2 decimales")
        BigDecimal porcentajeDescuento) {
}
