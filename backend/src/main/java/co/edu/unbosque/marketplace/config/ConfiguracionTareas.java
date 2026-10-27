package co.edu.unbosque.marketplace.config;

import co.edu.unbosque.marketplace.identidad.services.SesionService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Tareas programadas de la aplicación. */
@Configuration
@EnableScheduling
public class ConfiguracionTareas {

    private final SesionService sesiones;

    public ConfiguracionTareas(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    /** Primera corrida a los 5 minutos del arranque y luego cada hora (D10). */
    @Scheduled(initialDelay = 5, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void limpiarSesionesVencidas() {
        sesiones.limpiarVencidas();
    }
}
