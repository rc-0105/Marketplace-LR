package co.edu.unbosque.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import co.edu.unbosque.marketplace.identidad.infraestructura.EnvioCodigoRecuperacionPorCorreo;
import co.edu.unbosque.marketplace.identidad.infraestructura.EnvioCodigoRecuperacionPorLog;
import co.edu.unbosque.marketplace.identidad.services.EnvioCodigoRecuperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;

class ConfiguracionCorreoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withBean("applicationTaskExecutor", TaskExecutor.class, SyncTaskExecutor::new)
            .withPropertyValues("correo.remitente=plataforma@gmail.com")
            .withUserConfiguration(ConfiguracionCorreo.class);

    @Test
    @DisplayName("Sin correo.modo el código se escribe en el log")
    void usaElLogPorDefecto() {
        contexto.run(app -> assertThat(app).getBean(EnvioCodigoRecuperacion.class)
                .isInstanceOf(EnvioCodigoRecuperacionPorLog.class));
    }

    @Test
    @DisplayName("Con correo.modo=smtp el código se envía por correo, sin importar mayúsculas ni espacios")
    void usaSmtpCuandoSePide() {
        contexto.withPropertyValues("correo.modo= SMTP ").run(app -> assertThat(app)
                .getBean(EnvioCodigoRecuperacion.class).isInstanceOf(EnvioCodigoRecuperacionPorCorreo.class));
    }

    @Test
    @DisplayName("Existe un único bean del canal de envío, sin ambigüedad para quien lo inyecta")
    void registraUnSoloCanal() {
        contexto.withPropertyValues("correo.modo=smtp")
                .run(app -> assertThat(app).hasSingleBean(EnvioCodigoRecuperacion.class));
    }

    @Test
    @DisplayName("Un correo.modo desconocido impide arrancar con un mensaje que dice los valores válidos")
    void rechazaUnModoDesconocido() {
        contexto.withPropertyValues("correo.modo=smpt").run(app -> assertThat(app).hasFailed()
                .getFailure().rootCause().hasMessageContaining("'log' o 'smtp'").hasMessageContaining("smpt"));
    }
}
