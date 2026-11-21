package co.edu.unbosque.marketplace.config;

import co.edu.unbosque.marketplace.compartido.idempotencia.ServicioIdempotencia;
import co.edu.unbosque.marketplace.identidad.services.SesionService;
import co.edu.unbosque.marketplace.reservas.services.RegistroReservas;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Tareas programadas de la aplicación. */
@Configuration
@EnableScheduling
public class ConfiguracionTareas {

    private final SesionService sesiones;
    private final RegistroReservas reservas;
    private final ServicioIdempotencia idempotencia;

    public ConfiguracionTareas(SesionService sesiones, RegistroReservas reservas,
            ServicioIdempotencia idempotencia) {
        this.sesiones = sesiones;
        this.reservas = reservas;
        this.idempotencia = idempotencia;
    }

    /** Primera corrida a los 5 minutos del arranque y luego cada hora (D10). */
    @Scheduled(initialDelay = 5, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void limpiarSesionesVencidas() {
        sesiones.limpiarVencidas();
    }

    /**
     * Cada minuto pasa a VENCIDA los apartados PENDIENTE agotados (SOF-33, ADR-4). Las consultas ya los
     * ignoran, pero la restricción de exclusión de la base de datos no puede usar {@code now()} y solo suelta
     * el rango cuando la fila sale de PENDIENTE.
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void vencerApartadosDeReservas() {
        reservas.vencerCaducados();
    }

    /** Cada hora elimina las claves de idempotencia vencidas (SOF-77); la primera corrida a los 10 minutos. */
    @Scheduled(initialDelay = 10, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void limpiarClavesDeIdempotencia() {
        idempotencia.limpiarVencidas();
    }
}
