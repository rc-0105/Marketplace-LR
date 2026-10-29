package co.edu.unbosque.marketplace.identidad.infraestructura;

import co.edu.unbosque.marketplace.identidad.services.EnvioCodigoRecuperacion;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Correo simulado: el código se escribe en el log de la aplicación, nunca en la
 * respuesta HTTP, porque devolverlo permitiría tomar cualquier cuenta conociendo
 * su correo. Sale por su propio logger, separado de la auditoría (RF-45).
 */
@Component
@ConditionalOnProperty(name = "correo.modo", havingValue = "log", matchIfMissing = true)
public class EnvioCodigoRecuperacionPorLog implements EnvioCodigoRecuperacion {

    private static final Logger CORREO_SIMULADO = LoggerFactory.getLogger("correo.simulado");

    @Override
    public void enviar(String correo, String codigo, Instant venceEn) {
        CORREO_SIMULADO.info("Correo simulado para {}: tu código de recuperación es {} y vence {}",
                correo, codigo, venceEn);
    }
}
