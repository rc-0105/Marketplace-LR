package co.edu.unbosque.marketplace.identidad.infraestructura;

import co.edu.unbosque.marketplace.identidad.services.EnvioCodigoRecuperacion;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envía el código por SMTP cuando {@code correo.modo=smtp}. El envío sale en
 * segundo plano y después del commit: así un correo registrado no tarda más ni
 * falla distinto que uno inexistente (anti-enumeración), y si la solicitud se
 * revierte no llega un código que no quedó guardado.
 */
@Component
@ConditionalOnProperty(name = "correo.modo", havingValue = "smtp")
public class EnvioCodigoRecuperacionPorCorreo implements EnvioCodigoRecuperacion {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioCodigoRecuperacionPorCorreo.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);
    private static final String ASUNTO = "Tu código para restablecer la contraseña";

    private final JavaMailSender correo;
    private final TaskExecutor ejecutor;
    private final String remitente;

    public EnvioCodigoRecuperacionPorCorreo(JavaMailSender correo,
            @Qualifier("applicationTaskExecutor") TaskExecutor ejecutor,
            @Value("${correo.remitente:}") String remitente, @Value("${spring.mail.username:}") String usuario) {
        String elegido = remitente == null || remitente.isBlank() ? usuario : remitente;
        if (elegido == null || elegido.isBlank()) {
            throw new IllegalStateException("Con correo.modo=smtp hay que definir MAIL_FROM o MAIL_USERNAME.");
        }
        this.correo = correo;
        this.ejecutor = ejecutor;
        this.remitente = elegido.trim();
    }

    @Override
    public void enviar(String destinatario, String codigo, Instant venceEn) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject(ASUNTO);
        mensaje.setText("""
                Hola,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en Inversiones LR.

                Tu código es: %s

                Vence hoy a las %s (hora de Colombia) y solo sirve una vez.
                Si no pediste este cambio, ignora este correo: tu contraseña sigue igual.
                """.formatted(codigo, HORA.format(venceEn)));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ejecutor.execute(() -> despachar(mensaje));
                }
            });
        } else {
            ejecutor.execute(() -> despachar(mensaje));
        }
    }

    private void despachar(SimpleMailMessage mensaje) {
        try {
            correo.send(mensaje);
        } catch (MailException error) {
            // Solo el tipo de error: el mensaje del correo lleva el código y no debe quedar en el log.
            LOG.error("No se pudo enviar el correo de recuperación: {}", error.getClass().getSimpleName());
        }
    }
}
