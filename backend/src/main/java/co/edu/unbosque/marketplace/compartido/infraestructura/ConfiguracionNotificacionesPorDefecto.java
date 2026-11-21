package co.edu.unbosque.marketplace.compartido.infraestructura;

import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementación provisional del puerto de notificaciones: no avisa a nadie. El módulo
 * {@code notificaciones} aporta la real como un {@code @Service} escaneado; igual que en
 * {@link ConfiguracionReservasPorDefecto}, solo con un bean escaneado el orden de la condición es seguro.
 */
@Configuration
public class ConfiguracionNotificacionesPorDefecto {

    @Bean
    @ConditionalOnMissingBean(Notificador.class)
    Notificador notificadorPorDefecto() {
        return notificacion -> {
        };
    }
}
