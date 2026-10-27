package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import co.edu.unbosque.marketplace.identidad.services.SesionService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** La limpieza corre cada hora y arranca 5 minutos después del inicio (D10, REQ-A7). */
class ConfiguracionTareasTest {

    @Test
    @DisplayName("La tarea delega la limpieza en el servicio de sesiones")
    void tareaInvocaLaLimpieza() {
        SesionService sesiones = mock(SesionService.class);

        new ConfiguracionTareas(sesiones).limpiarSesionesVencidas();

        verify(sesiones).limpiarVencidas();
    }

    @Test
    @DisplayName("Se programa con 5 minutos de espera inicial y una hora entre corridas")
    void programacionSegunDiseno() throws NoSuchMethodException {
        Scheduled programacion = ConfiguracionTareas.class
                .getMethod("limpiarSesionesVencidas")
                .getAnnotation(Scheduled.class);

        assertThat(programacion.timeUnit()).isEqualTo(TimeUnit.MINUTES);
        assertThat(programacion.initialDelay()).isEqualTo(5);
        assertThat(programacion.fixedDelay()).isEqualTo(60);
    }
}
