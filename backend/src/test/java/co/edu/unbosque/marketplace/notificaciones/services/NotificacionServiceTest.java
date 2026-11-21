package co.edu.unbosque.marketplace.notificaciones.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.notificaciones.exceptions.NotificacionNoEncontradaException;
import co.edu.unbosque.marketplace.notificaciones.model.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.repository.NotificacionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class NotificacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-21T15:00:00Z");
    private static final Long USUARIO_ID = 7L;
    private static final Long OTRO_USUARIO_ID = 8L;
    private static final Long NOTIFICACION_ID = 3L;

    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final RegistroNotificaciones registro = mock(RegistroNotificaciones.class);
    private final NotificacionService servicio = new NotificacionService(notificaciones, registro,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Notificar guarda la notificación sin leer, con el destinatario, el tipo, el texto y la entidad")
    void notificarGuardaLaNotificacion() {
        servicio.notificar(new NuevaNotificacion(USUARIO_ID, TipoNotificacion.RESERVA_CONFIRMADA, "Tu reserva quedó confirmada.",
                "Reserva", 42L));

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(registro).guardar(guardada.capture());
        Notificacion notificacion = guardada.getValue();
        assertThat(notificacion.getDestinatarioId()).isEqualTo(USUARIO_ID);
        assertThat(notificacion.getTipo()).isEqualTo(TipoNotificacion.RESERVA_CONFIRMADA);
        assertThat(notificacion.getMensaje()).isEqualTo("Tu reserva quedó confirmada.");
        assertThat(notificacion.getEntidad()).isEqualTo("Reserva");
        assertThat(notificacion.getEntidadId()).isEqualTo(42L);
        assertThat(notificacion.getCreadoEn()).isEqualTo(AHORA);
        assertThat(notificacion.isLeida()).isFalse();
    }

    @Test
    @DisplayName("Si guardar la notificación falla, el error se atrapa: la operación que avisa no se rompe")
    void unaFallaAlGuardarNoSePropaga() {
        doThrow(new DataIntegrityViolationException("sin conexión")).when(registro).guardar(any());

        assertThatCode(() -> servicio.notificar(new NuevaNotificacion(USUARIO_ID, TipoNotificacion.CUENTA_RESTRINGIDA,
                "Un administrador restringió tu cuenta. Motivo: spam", "Usuario", USUARIO_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Listar trae las notificaciones del usuario, de la más nueva a la más vieja, y cuántas no ha leído")
    void listarTraeLasDelUsuarioYElContador() {
        Notificacion nueva = notificacion(USUARIO_ID);
        Notificacion vieja = notificacion(USUARIO_ID);
        when(notificaciones.findTop50ByDestinatarioIdOrderByCreadoEnDescIdDesc(USUARIO_ID))
                .thenReturn(List.of(nueva, vieja));
        when(notificaciones.countByDestinatarioIdAndLeidaEnIsNull(USUARIO_ID)).thenReturn(2L);

        BandejaNotificaciones bandeja = servicio.listar(USUARIO_ID);

        assertThat(bandeja.noLeidas()).isEqualTo(2L);
        assertThat(bandeja.notificaciones()).containsExactly(nueva, vieja);
    }

    @Test
    @DisplayName("Marcar como leída una notificación propia guarda cuándo se leyó")
    void marcarLeidaGuardaLaFecha() {
        Notificacion propia = notificacion(USUARIO_ID);
        when(notificaciones.findByIdAndDestinatarioId(NOTIFICACION_ID, USUARIO_ID)).thenReturn(Optional.of(propia));

        Notificacion leida = servicio.marcarLeida(NOTIFICACION_ID, USUARIO_ID);

        assertThat(leida.isLeida()).isTrue();
        assertThat(leida.getLeidaEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("Marcar otra vez una notificación ya leída la devuelve igual y conserva la primera fecha de lectura")
    void marcarLeidaEsIdempotente() {
        Notificacion propia = notificacion(USUARIO_ID);
        Instant primeraLectura = AHORA.minusSeconds(3600);
        propia.marcarLeida(primeraLectura);
        when(notificaciones.findByIdAndDestinatarioId(NOTIFICACION_ID, USUARIO_ID)).thenReturn(Optional.of(propia));

        Notificacion leida = servicio.marcarLeida(NOTIFICACION_ID, USUARIO_ID);

        assertThat(leida.getLeidaEn()).isEqualTo(primeraLectura);
    }

    @Test
    @DisplayName("Marcar una notificación ajena o inexistente responde 404 sin revelar si existe")
    void marcarAjenaOInexistenteResponde404() {
        when(notificaciones.findByIdAndDestinatarioId(NOTIFICACION_ID, OTRO_USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarLeida(NOTIFICACION_ID, OTRO_USUARIO_ID))
                .isInstanceOfSatisfying(NotificacionNoEncontradaException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getMessage()).isEqualTo("No encontramos esa notificación.");
                });
    }

    private static Notificacion notificacion(Long destinatarioId) {
        return Notificacion.nueva(new NuevaNotificacion(destinatarioId, TipoNotificacion.RESERVA_CONFIRMADA,
                "Tu reserva quedó confirmada.", "Reserva", 42L), AHORA.minusSeconds(60));
    }
}
