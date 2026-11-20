package co.edu.unbosque.marketplace.cotizacion.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.cotizacion.model.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.repository.ConfiguracionCotizacionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porcentajes de tarifa de servicio y descuento que el administrador ajusta sin desplegar (RF-34).
 * La cotización los lee en cada cálculo, así que el cambio vale de inmediato para las cotizaciones nuevas.
 */
@Service
public class ConfiguracionTarifasService {

    private static final String ENTIDAD = "ConfiguracionCotizacion";

    private final ConfiguracionCotizacionRepository configuraciones;
    private final PublicadorEventos publicador;

    public ConfiguracionTarifasService(ConfiguracionCotizacionRepository configuraciones,
            PublicadorEventos publicador) {
        this.configuraciones = configuraciones;
        this.publicador = publicador;
    }

    @Transactional(readOnly = true)
    public ConfiguracionCotizacion consultar() {
        return buscar();
    }

    /** Los porcentajes ya llegan dentro del rango permitido; el cambio queda como acción administrativa. */
    @Transactional
    public ConfiguracionCotizacion actualizar(Long administradorId, BigDecimal porcentajeServicio,
            BigDecimal porcentajeDescuento) {
        ConfiguracionCotizacion configuracion = buscar();
        configuracion.cambiar(porcentajeServicio, porcentajeDescuento);
        ConfiguracionCotizacion guardada = configuraciones.save(configuracion);
        publicador.publicar(new EventoCritico(String.valueOf(administradorId), AccionCritica.ACCION_ADMINISTRATIVA,
                ENTIDAD, String.valueOf(ConfiguracionCotizacion.ID), ResultadoEvento.EXITO, Instant.now(),
                Map.of("operacion", "configurar_tarifas", "porcentajeServicio", porcentajeServicio.toPlainString(),
                        "porcentajeDescuento", porcentajeDescuento.toPlainString())));
        return guardada;
    }

    private ConfiguracionCotizacion buscar() {
        Optional<ConfiguracionCotizacion> encontrada = configuraciones.findById(ConfiguracionCotizacion.ID);
        if (encontrada.isEmpty()) {
            throw new IllegalStateException("Falta la configuración de la cotización");
        }
        return encontrada.get();
    }
}
