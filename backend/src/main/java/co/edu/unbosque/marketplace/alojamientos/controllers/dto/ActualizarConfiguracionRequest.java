package co.edu.unbosque.marketplace.alojamientos.controllers.dto;

import co.edu.unbosque.marketplace.alojamientos.services.SolicitudConfiguracion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;


public record ActualizarConfiguracionRequest(
        @DecimalMin(value = "0", message = "La tarifa de limpieza no puede ser negativa.")
        @Digits(integer = 10, fraction = 2, message = "La tarifa de limpieza admite máximo 10 enteros y 2 decimales.")
        BigDecimal tarifaLimpieza,

        @Pattern(regexp = "INMEDIATA|APROBACION", message = "El modo de reserva debe ser INMEDIATA o APROBACION.")
        String modoReserva,

        Long politicaCancelacion,

        @Size(max = 20, message = "Se admiten máximo 20 servicios adicionales.")
        List<@Valid @NotNull(message = "Un servicio adicional no puede estar vacío.") ServicioAdicional> serviciosAdicionales) {

    public record ServicioAdicional(
            @NotBlank(message = "El nombre del servicio adicional es obligatorio.")
            @Size(max = 80, message = "El nombre del servicio adicional admite máximo 80 caracteres.")
            String nombre,

            @NotNull(message = "El costo del servicio adicional es obligatorio.")
            @Positive(message = "El costo del servicio adicional debe ser mayor que cero.")
            @Digits(integer = 10, fraction = 2, message = "El costo del servicio adicional admite máximo 10 enteros y 2 decimales.")
            BigDecimal costo) {
    }

    public SolicitudConfiguracion aSolicitud() {
        List<SolicitudConfiguracion.ServicioAdicional> adicionales = serviciosAdicionales == null
                ? List.of()
                : serviciosAdicionales.stream()
                        .map(adicional -> new SolicitudConfiguracion.ServicioAdicional(adicional.nombre(),
                                adicional.costo()))
                        .toList();
        return new SolicitudConfiguracion(tarifaLimpieza, modoReserva, politicaCancelacion, adicionales);
    }
}
