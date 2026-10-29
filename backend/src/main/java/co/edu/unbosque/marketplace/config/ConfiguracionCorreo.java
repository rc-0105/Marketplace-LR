package co.edu.unbosque.marketplace.config;

import co.edu.unbosque.marketplace.identidad.infraestructura.EnvioCodigoRecuperacionPorCorreo;
import co.edu.unbosque.marketplace.identidad.infraestructura.EnvioCodigoRecuperacionPorLog;
import co.edu.unbosque.marketplace.identidad.services.EnvioCodigoRecuperacion;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige el único canal por el que sale el código de recuperación (RF-05), según
 * {@code correo.modo}: "log" (por defecto) o "smtp". Un solo bean evita que haya
 * dos candidatos para {@link EnvioCodigoRecuperacion}.
 */
@Configuration
public class ConfiguracionCorreo {

    @Bean
    public EnvioCodigoRecuperacion envioCodigoRecuperacion(
            @Value("${correo.modo:log}") String modo,
            ObjectProvider<JavaMailSender> correo,
            @Qualifier("applicationTaskExecutor") TaskExecutor ejecutor,
            @Value("${correo.remitente:}") String remitente,
            @Value("${spring.mail.username:}") String usuario) {
        return switch (modo.trim().toLowerCase(Locale.ROOT)) {
            case "log" -> new EnvioCodigoRecuperacionPorLog();
            case "smtp" -> new EnvioCodigoRecuperacionPorCorreo(correo.getObject(), ejecutor, remitente, usuario);
            default -> throw new IllegalStateException(
                    "CORREO_MODO debe ser 'log' o 'smtp', no '%s'.".formatted(modo));
        };
    }
}
