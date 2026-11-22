package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** AC1 de SOF-37: huésped y anfitrión reciben una notificación con el alojamiento y las fechas. */
class NotificacionesReservaTest {

    private static final Long RESERVA_ID = 42L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Long HUESPED_ID = 7L;
    private static final Long ANFITRION_ID = 9L;

    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final Notificador notificador = mock(Notificador.class);
    private final NotificacionesReserva notificaciones = new NotificacionesReserva(publicaciones, notificador);

    @BeforeEach
    void preparar() {
        when(publicaciones.consultarReservados(Set.of(PUBLICACION_ID))).thenReturn(Map.of(PUBLICACION_ID,
                new AlojamientoReservado(PUBLICACION_ID, "Casa en la playa", "Santa Marta", null, Map.of(), null)));
        when(publicaciones.consultarAcceso(PUBLICACION_ID))
                .thenReturn(Optional.of(new AccesoPublicacion(ANFITRION_ID, true)));
    }

    @Test
    @DisplayName("Una reserva confirmada avisa al huésped y al anfitrión con el alojamiento y las fechas")
    void confirmadaAvisaAHuespedYAnfitrion() {
        notificaciones.notificar(reserva(), TipoNotificacion.RESERVA_CONFIRMADA);

        assertThat(enviadas()).containsExactly(
                new NuevaNotificacion(HUESPED_ID, TipoNotificacion.RESERVA_CONFIRMADA,
                        "Tu reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 quedó confirmada.",
                        "Reserva", RESERVA_ID),
                new NuevaNotificacion(ANFITRION_ID, TipoNotificacion.RESERVA_CONFIRMADA,
                        "Se confirmó una reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026.",
                        "Reserva", RESERVA_ID));
    }

    @Test
    @DisplayName("Una reserva rechazada por el pago avisa al huésped y al anfitrión con el motivo")
    void rechazadaAvisaAHuespedYAnfitrion() {
        notificaciones.notificar(reserva(), TipoNotificacion.RESERVA_RECHAZADA);

        assertThat(enviadas()).containsExactly(
                new NuevaNotificacion(HUESPED_ID, TipoNotificacion.RESERVA_RECHAZADA,
                        "Tu reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 fue rechazada porque el pago "
                                + "no fue aprobado.", "Reserva", RESERVA_ID),
                new NuevaNotificacion(ANFITRION_ID, TipoNotificacion.RESERVA_RECHAZADA,
                        "Se rechazó una reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 porque el pago "
                                + "no fue aprobado.", "Reserva", RESERVA_ID));
    }

    @Test
    @DisplayName("Una reserva cancelada avisa al huésped y al anfitrión")
    void canceladaAvisaAHuespedYAnfitrion() {
        notificaciones.notificar(reserva(), TipoNotificacion.RESERVA_CANCELADA);

        assertThat(enviadas()).containsExactly(
                new NuevaNotificacion(HUESPED_ID, TipoNotificacion.RESERVA_CANCELADA,
                        "Tu reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 fue cancelada.",
                        "Reserva", RESERVA_ID),
                new NuevaNotificacion(ANFITRION_ID, TipoNotificacion.RESERVA_CANCELADA,
                        "Se canceló una reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026.",
                        "Reserva", RESERVA_ID));
    }

    @Test
    @DisplayName("Si no se puede consultar el alojamiento, no avisa y no rompe la operación de la reserva")
    void unaFallaAlConsultarNoRompeLaOperacion() {
        when(publicaciones.consultarReservados(Set.of(PUBLICACION_ID))).thenThrow(new IllegalStateException("caída"));

        assertThatCode(() -> notificaciones.notificar(reserva(), TipoNotificacion.RESERVA_CONFIRMADA))
                .doesNotThrowAnyException();
        verify(notificador, never()).notificar(any());
    }

    @Test
    @DisplayName("SOF-79: aprobada, rechazada con motivo y vencida avisan solo al huésped")
    void respuestasDeSolicitudAvisanSoloAlHuesped() {
        notificaciones.notificarHuesped(reserva(), TipoNotificacion.SOLICITUD_APROBADA, null);
        notificaciones.notificarHuesped(reserva(), TipoNotificacion.SOLICITUD_RECHAZADA, "No disponible");
        notificaciones.notificarHuesped(reserva(), TipoNotificacion.SOLICITUD_VENCIDA, null);

        assertThat(enviadas(3)).containsExactly(
                new NuevaNotificacion(HUESPED_ID, TipoNotificacion.SOLICITUD_APROBADA,
                        "Tu solicitud de reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 fue aprobada: "
                                + "ya puedes pagarla para confirmar la reserva.", "Reserva", RESERVA_ID),
                new NuevaNotificacion(HUESPED_ID, TipoNotificacion.SOLICITUD_RECHAZADA,
                        "Tu solicitud de reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 fue rechazada "
                                + "por el anfitrión. Motivo: No disponible", "Reserva", RESERVA_ID),
                new NuevaNotificacion(HUESPED_ID, TipoNotificacion.SOLICITUD_VENCIDA,
                        "Tu solicitud de reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026 venció porque "
                                + "el anfitrión no respondió a tiempo.", "Reserva", RESERVA_ID));
    }

    @Test
    @DisplayName("SOF-79: un motivo de 500 caracteres se recorta para que el aviso quepa en los 600 de la columna")
    void elMotivoLargoSeRecorta() {
        notificaciones.notificarHuesped(reserva(), TipoNotificacion.SOLICITUD_RECHAZADA, "x".repeat(500));

        String mensaje = enviadas(1).get(0).mensaje();
        assertThat(mensaje.length()).isLessThanOrEqualTo(600);
        assertThat(mensaje).endsWith("…");
    }

    @Test
    @DisplayName("SOF-79: si no se puede consultar el alojamiento, el aviso al huésped no lanza")
    void unaFallaAlAvisarAlHuespedNoLanza() {
        when(publicaciones.consultarReservados(Set.of(PUBLICACION_ID))).thenThrow(new IllegalStateException("caída"));

        assertThatCode(() -> notificaciones.notificarHuesped(reserva(), TipoNotificacion.SOLICITUD_VENCIDA, null))
                .doesNotThrowAnyException();
        verify(notificador, never()).notificar(any());
    }

    @Test
    @DisplayName("SOF-79: una solicitud recibida avisa solo al anfitrión, con el alojamiento y las fechas")
    void solicitudRecibidaAvisaSoloAlAnfitrion() {
        notificaciones.notificarAnfitrion(reserva(), TipoNotificacion.SOLICITUD_RECIBIDA);

        ArgumentCaptor<NuevaNotificacion> captor = ArgumentCaptor.forClass(NuevaNotificacion.class);
        verify(notificador, times(1)).notificar(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new NuevaNotificacion(ANFITRION_ID, TipoNotificacion.SOLICITUD_RECIBIDA,
                "Tienes una nueva solicitud de reserva en «Casa en la playa» del 10/12/2026 al 14/12/2026.",
                "Reserva", RESERVA_ID));
    }

    @Test
    @DisplayName("SOF-79: si el aviso al anfitrión falla, no rompe la operación")
    void unaFallaAlAvisarAlAnfitrionNoRompe() {
        when(publicaciones.consultarReservados(Set.of(PUBLICACION_ID))).thenThrow(new IllegalStateException("caída"));

        assertThatCode(() -> notificaciones.notificarAnfitrion(reserva(), TipoNotificacion.SOLICITUD_RECIBIDA))
                .doesNotThrowAnyException();
        verify(notificador, never()).notificar(any());
    }

    private java.util.List<NuevaNotificacion> enviadas() {
        return enviadas(2);
    }

    private java.util.List<NuevaNotificacion> enviadas(int veces) {
        ArgumentCaptor<NuevaNotificacion> captor = ArgumentCaptor.forClass(NuevaNotificacion.class);
        verify(notificador, times(veces)).notificar(captor.capture());
        return captor.getAllValues();
    }

    private static Reserva reserva() {
        Reserva reserva = Reserva.nueva(PUBLICACION_ID, HUESPED_ID, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 14), 4, new BigDecimal("400000"), new BigDecimal("30000"),
                new BigDecimal("40000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("470000"), Set.of(),
                Instant.parse("2026-12-01T15:10:00Z"));
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        return reserva;
    }
}
