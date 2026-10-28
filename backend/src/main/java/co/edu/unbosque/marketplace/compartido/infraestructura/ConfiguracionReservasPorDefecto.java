package co.edu.unbosque.marketplace.compartido.infraestructura;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementación provisional del puerto de reservas: no hay reservas, así que nada bloquea la baja.
 *
 * <p>HU-22 aporta la implementación real como un {@code @Service} o {@code @Component} escaneado. La
 * condición sobre un {@code @Bean} se evalúa después del escaneo, de modo que solo con un bean
 * escaneado el orden es seguro; un {@code @Bean} de otra {@code @Configuration} no lo garantiza.
 */
@Configuration
public class ConfiguracionReservasPorDefecto {

    @Bean
    @ConditionalOnMissingBean(ConsultaReservasFuturas.class)
    ConsultaReservasFuturas consultaReservasFuturasPorDefecto() {
        return usuarioId -> 0L;
    }
}
