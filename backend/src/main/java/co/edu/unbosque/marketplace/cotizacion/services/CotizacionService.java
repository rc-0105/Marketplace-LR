package co.edu.unbosque.marketplace.cotizacion.services;

import co.edu.unbosque.marketplace.alojamientos.services.DetalleAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.alojamientos.services.ServicioAdicionalDetalle;
import co.edu.unbosque.marketplace.cotizacion.exceptions.CotizacionInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.model.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.repository.ConfiguracionCotizacionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cotización de una estancia (RF-32, RF-33). La tarifa de servicio y el descuento son un porcentaje
 * del precio base; la limpieza y los servicios adicionales se suman tal como los fijó el anfitrión.
 */
@Service
public class CotizacionService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final PublicacionService publicaciones;
    private final ConfiguracionCotizacionRepository configuraciones;

    public CotizacionService(PublicacionService publicaciones, ConfiguracionCotizacionRepository configuraciones) {
        this.publicaciones = publicaciones;
        this.configuraciones = configuraciones;
    }

    /**
     * Si la publicación no existe o no está activa responde 404. La cotización no guarda nada: quitar
     * un servicio adicional de la solicitud devuelve el total anterior.
     */
    @Transactional(readOnly = true)
    public Cotizacion cotizar(Long publicacionId, LocalDate llegada, LocalDate salida, Set<Long> adicionalesElegidos) {
        if (!salida.isAfter(llegada)) {
            throw new CotizacionInvalidaException("La fecha de salida debe ser posterior a la de llegada");
        }
        DetalleAlojamiento alojamiento = publicaciones.consultarActiva(publicacionId);
        Optional<ConfiguracionCotizacion> encontrada = configuraciones.findById(ConfiguracionCotizacion.ID);
        if (encontrada.isEmpty()) {
            throw new IllegalStateException("Falta la configuración de la cotización");
        }
        ConfiguracionCotizacion configuracion = encontrada.get();

        long noches = ChronoUnit.DAYS.between(llegada, salida);
        BigDecimal precioBase = alojamiento.precioNoche().multiply(BigDecimal.valueOf(noches));
        BigDecimal limpieza = BigDecimal.ZERO;
        if (alojamiento.tarifaLimpieza() != null) {
            limpieza = alojamiento.tarifaLimpieza();
        }
        BigDecimal tarifaServicio = porcentaje(precioBase, configuracion.getPorcentajeServicio());
        BigDecimal descuento = porcentaje(precioBase, configuracion.getPorcentajeDescuento());

        List<ServicioAdicionalDetalle> adicionales = elegidos(alojamiento, adicionalesElegidos);
        BigDecimal totalAdicionales = BigDecimal.ZERO;
        for (ServicioAdicionalDetalle adicional : adicionales) {
            totalAdicionales = totalAdicionales.add(adicional.costo());
        }

        BigDecimal total = precioBase.add(limpieza).add(tarifaServicio).add(totalAdicionales).subtract(descuento);
        return new Cotizacion(publicacionId, noches, alojamiento.precioNoche(), precioBase, limpieza,
                tarifaServicio, descuento, adicionales, totalAdicionales, total);
    }

    private List<ServicioAdicionalDetalle> elegidos(DetalleAlojamiento alojamiento, Set<Long> ids) {
        List<ServicioAdicionalDetalle> elegidos = new ArrayList<>();
        if (ids == null) {
            return elegidos;
        }
        for (ServicioAdicionalDetalle adicional : alojamiento.serviciosAdicionales()) {
            if (ids.contains(adicional.id())) {
                elegidos.add(adicional);
            }
        }
        if (elegidos.size() != ids.size()) {
            throw new CotizacionInvalidaException("Un servicio adicional no pertenece a esta publicación");
        }
        return elegidos;
    }

    private BigDecimal porcentaje(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
    }
}
