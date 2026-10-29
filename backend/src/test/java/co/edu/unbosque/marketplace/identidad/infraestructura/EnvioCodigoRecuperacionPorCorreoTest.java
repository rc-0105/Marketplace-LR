package co.edu.unbosque.marketplace.identidad.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EnvioCodigoRecuperacionPorCorreoTest {

    private static final Instant VENCE = Instant.parse("2026-10-28T18:15:00Z");

    private final JavaMailSender correo = mock(JavaMailSender.class);
    private final EnvioCodigoRecuperacionPorCorreo envio = new EnvioCodigoRecuperacionPorCorreo(
            correo, new SyncTaskExecutor(), "plataforma@gmail.com", "cuenta@gmail.com");

    @BeforeEach
    void prepararCorreo() {
        when(correo.createMimeMessage()).thenAnswer(invocacion -> new MimeMessage((Session) null));
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Envía al usuario un correo con remitente de la marca, asunto y el código")
    void enviaElCodigoPorCorreo() throws Exception {
        envio.enviar("ana@correo.com", "482913", VENCE);

        MimeMessage mensaje = mensajeEnviado();
        assertThat(mensaje.getAllRecipients()).extracting(Object::toString).containsExactly("ana@correo.com");
        InternetAddress remitente = (InternetAddress) mensaje.getFrom()[0];
        assertThat(remitente.getAddress()).isEqualTo("plataforma@gmail.com");
        assertThat(remitente.getPersonal()).isEqualTo("Inversiones LR");
        assertThat(mensaje.getSubject()).isEqualTo("Tu código para restablecer la contraseña");
    }

    @Test
    @DisplayName("El correo trae versión HTML con la marca y versión de texto, ambas con el código y la hora")
    void incluyeVersionHtmlYTexto() throws Exception {
        envio.enviar("ana@correo.com", "482913", VENCE);

        MimeMessage mensaje = mensajeEnviado();
        String html = parte(mensaje, "text/html");
        String texto = parte(mensaje, "text/plain");
        assertThat(html).contains("482913", "13:15", "Inversiones LR", "Restablece tu contraseña", "2026")
                .doesNotContain("{{");
        assertThat(texto).contains("482913", "13:15", "Inversiones LR").doesNotContain("{{", "<");
    }

    @Test
    @DisplayName("Con MAIL_FROM vacío usa la cuenta SMTP como remitente")
    void usaLaCuentaSiFaltaElRemitente() throws Exception {
        new EnvioCodigoRecuperacionPorCorreo(correo, new SyncTaskExecutor(), "", "cuenta@gmail.com")
                .enviar("ana@correo.com", "482913", VENCE);

        assertThat(((InternetAddress) mensajeEnviado().getFrom()[0]).getAddress()).isEqualTo("cuenta@gmail.com");
    }

    @Test
    @DisplayName("Dentro de una transacción espera al commit antes de enviar")
    void enviaSoloDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();

        envio.enviar("ana@correo.com", "482913", VENCE);

        verify(correo, never()).send(any(MimeMessage.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(correo).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Si la transacción se revierte no envía nada")
    void noEnviaSiSeRevierte() {
        TransactionSynchronizationManager.initSynchronization();

        envio.enviar("ana@correo.com", "482913", VENCE);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(correo, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Un fallo del SMTP no se propaga, para que la respuesta no revele si el correo existe")
    void absorbeFallosDelSmtp() {
        doThrow(new MailSendException("smtp caído")).when(correo).send(any(MimeMessage.class));

        assertThatCode(() -> envio.enviar("ana@correo.com", "482913", VENCE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sin remitente ni cuenta configurados no arranca en modo smtp")
    void exigeRemitente() {
        TaskExecutor ejecutor = new SyncTaskExecutor();

        assertThatThrownBy(() -> new EnvioCodigoRecuperacionPorCorreo(correo, ejecutor, " ", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }

    private MimeMessage mensajeEnviado() {
        ArgumentCaptor<MimeMessage> mensaje = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correo).send(mensaje.capture());
        return mensaje.getValue();
    }

    /** Busca dentro del MIME la parte del tipo pedido; saveChanges actualiza los encabezados Content-Type. */
    private static String parte(MimeMessage mensaje, String tipo) throws Exception {
        mensaje.saveChanges();
        String encontrada = buscar(mensaje, tipo);
        assertThat(encontrada).as("parte " + tipo).isNotNull();
        return encontrada;
    }

    private static String buscar(Part parte, String tipo) throws Exception {
        if (parte.isMimeType(tipo)) {
            return (String) parte.getContent();
        }
        if (parte.getContent() instanceof Multipart multiparte) {
            for (int i = 0; i < multiparte.getCount(); i++) {
                BodyPart hija = multiparte.getBodyPart(i);
                String encontrada = buscar(hija, tipo);
                if (encontrada != null) {
                    return encontrada;
                }
            }
        }
        return null;
    }
}
