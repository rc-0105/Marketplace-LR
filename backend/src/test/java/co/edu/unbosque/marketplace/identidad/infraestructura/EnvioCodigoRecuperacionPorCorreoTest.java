package co.edu.unbosque.marketplace.identidad.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.unbosque.marketplace.identidad.services.EnvioCodigoRecuperacion;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EnvioCodigoRecuperacionPorCorreoTest {

    private static final Instant VENCE = Instant.parse("2026-10-28T18:15:00Z");

    private final JavaMailSender correo = mock(JavaMailSender.class);
    private final EnvioCodigoRecuperacionPorCorreo envio =
            new EnvioCodigoRecuperacionPorCorreo(correo, new SyncTaskExecutor(), "plataforma@gmail.com", "cuenta@gmail.com");

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Envía al correo del usuario un mensaje con el código y la hora de vencimiento en Colombia")
    void enviaElCodigoPorCorreo() {
        envio.enviar("ana@correo.com", "482913", VENCE);

        SimpleMailMessage mensaje = mensajeEnviado();
        assertThat(mensaje.getTo()).containsExactly("ana@correo.com");
        assertThat(mensaje.getFrom()).isEqualTo("plataforma@gmail.com");
        assertThat(mensaje.getSubject()).isEqualTo("Tu código para restablecer la contraseña");
        assertThat(mensaje.getText()).contains("482913").contains("13:15");
    }

    @Test
    @DisplayName("Dentro de una transacción espera al commit antes de enviar")
    void enviaSoloDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();

        envio.enviar("ana@correo.com", "482913", VENCE);

        verify(correo, never()).send(any(SimpleMailMessage.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(correo).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Si la transacción se revierte no envía nada")
    void noEnviaSiSeRevierte() {
        TransactionSynchronizationManager.initSynchronization();

        envio.enviar("ana@correo.com", "482913", VENCE);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(correo, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Un fallo del SMTP no se propaga, para que la respuesta no revele si el correo existe")
    void absorbeFallosDelSmtp() {
        doThrow(new MailSendException("smtp caído")).when(correo).send(any(SimpleMailMessage.class));

        assertThatCode(() -> envio.enviar("ana@correo.com", "482913", VENCE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Con MAIL_FROM vacío usa la cuenta SMTP como remitente")
    void usaLaCuentaSiFaltaElRemitente() {
        new EnvioCodigoRecuperacionPorCorreo(correo, new SyncTaskExecutor(), "", "cuenta@gmail.com")
                .enviar("ana@correo.com", "482913", VENCE);

        assertThat(mensajeEnviado().getFrom()).isEqualTo("cuenta@gmail.com");
    }

    @Test
    @DisplayName("Sin remitente ni cuenta configurados no arranca en modo smtp")
    void exigeRemitente() {
        TaskExecutor ejecutor = new SyncTaskExecutor();

        assertThatThrownBy(() -> new EnvioCodigoRecuperacionPorCorreo(correo, ejecutor, " ", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    @DisplayName("correo.modo elige el canal: log por defecto y smtp cuando se pide")
    void eligeElCanalSegunLaPropiedad() {
        ApplicationContextRunner contexto = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withBean(JavaMailSender.class, () -> correo)
                .withBean("applicationTaskExecutor", TaskExecutor.class, SyncTaskExecutor::new)
                .withPropertyValues("correo.remitente=plataforma@gmail.com")
                .withUserConfiguration(EnvioCodigoRecuperacionPorLog.class, EnvioCodigoRecuperacionPorCorreo.class);

        contexto.run(sinModo -> assertThat(sinModo.getBean(EnvioCodigoRecuperacion.class))
                .isInstanceOf(EnvioCodigoRecuperacionPorLog.class));
        contexto.withPropertyValues("correo.modo=smtp").run(smtp -> assertThat(
                smtp.getBean(EnvioCodigoRecuperacion.class)).isInstanceOf(EnvioCodigoRecuperacionPorCorreo.class));
    }

    private SimpleMailMessage mensajeEnviado() {
        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo).send(mensaje.capture());
        return mensaje.getValue();
    }
}
