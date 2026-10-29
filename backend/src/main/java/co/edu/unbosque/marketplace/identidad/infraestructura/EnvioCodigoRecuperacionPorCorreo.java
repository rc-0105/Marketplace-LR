package co.edu.unbosque.marketplace.identidad.infraestructura;

import co.edu.unbosque.marketplace.identidad.services.EnvioCodigoRecuperacion;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

/**
 * Envía el código por SMTP; lo registra {@code ConfiguracionCorreo} con {@code correo.modo=smtp}. El envío sale en
 * segundo plano y después del commit: así un correo registrado no tarda más ni
 * falla distinto que uno inexistente (anti-enumeración), y si la solicitud se
 * revierte no llega un código que no quedó guardado. El mensaje va en HTML con
 * la marca y en texto plano para los clientes que no muestran HTML; las
 * plantillas viven en {@code resources/correos}.
 */
public class EnvioCodigoRecuperacionPorCorreo implements EnvioCodigoRecuperacion {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioCodigoRecuperacionPorCorreo.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);
    private static final DateTimeFormatter ANIO = DateTimeFormatter.ofPattern("yyyy").withZone(ZONA);
    private static final String ASUNTO = "Tu código para restablecer la contraseña";
    private static final String NOMBRE_REMITENTE = "Inversiones LR";

    private final JavaMailSender correo;
    private final TaskExecutor ejecutor;
    private final String remitente;
    private final String plantillaHtml = leer("correos/recuperacion.html");
    private final String plantillaTexto = leer("correos/recuperacion.txt");

    public EnvioCodigoRecuperacionPorCorreo(JavaMailSender correo, TaskExecutor ejecutor, String remitente,
            String usuario) {
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
        Map<String, String> valores = Map.of("codigo", codigo, "hora", HORA.format(venceEn), "anio", ANIO.format(venceEn));
        String texto = completar(plantillaTexto, valores, false);
        String html = completar(plantillaHtml, valores, true);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ejecutor.execute(() -> despachar(destinatario, texto, html));
                }
            });
        } else {
            ejecutor.execute(() -> despachar(destinatario, texto, html));
        }
    }

    private void despachar(String destinatario, String texto, String html) {
        try {
            MimeMessage mensaje = correo.createMimeMessage();
            MimeMessageHelper contenido = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            contenido.setFrom(remitente, NOMBRE_REMITENTE);
            contenido.setTo(destinatario);
            contenido.setSubject(ASUNTO);
            contenido.setText(texto, html);
            correo.send(mensaje);
        } catch (MailException | MessagingException | UnsupportedEncodingException error) {
            // Solo el tipo de error: el mensaje del correo lleva el código y no debe quedar en el log.
            LOG.error("No se pudo enviar el correo de recuperación: {}", error.getClass().getSimpleName());
        }
    }

    private static String completar(String plantilla, Map<String, String> valores, boolean escaparHtml) {
        String resultado = plantilla;
        for (Map.Entry<String, String> valor : valores.entrySet()) {
            String reemplazo = escaparHtml ? HtmlUtils.htmlEscape(valor.getValue()) : valor.getValue();
            resultado = resultado.replace("{{" + valor.getKey() + "}}", reemplazo);
        }
        return resultado;
    }

    private static String leer(String ruta) {
        try {
            return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("No se encontró la plantilla de correo " + ruta, error);
        }
    }
}
