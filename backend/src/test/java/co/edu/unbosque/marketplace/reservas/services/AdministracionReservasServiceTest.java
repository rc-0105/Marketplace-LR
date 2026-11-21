package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.reservas.exceptions.ReembolsoFallidoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ResolucionInvalidaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoResolubleException;
import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.EstadoPago;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Resolución administrativa de las reservas en revisión manual (SOF-83, AC3, S7: devolución real). */
class AdministracionReservasServiceTest {

    private static final Long RESERVA_ID = 42L;
    private static final Long ADMINISTRADOR_ID = 1L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");
    private static final String TRANSACCION = "pi_123";

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final RegistroReservas registro = mock(RegistroReservas.class);
    private final PasarelaPagos pasarela = mock(PasarelaPagos.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final NotificacionesReserva notificaciones = mock(NotificacionesReserva.class);
    private final AdministracionReservasService servicio = new AdministracionReservasService(reservas, publicaciones,
            registro, pasarela, publicador, notificaciones, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Lista las reservas en revisión con el título y el motivo del bloqueo de su publicación")
    void listaLasReservasEnRevision() {
        Reserva primera = enRevision(TRANSACCION);
        when(reservas.findByEstadoOrderByLlegadaAscIdAsc(EstadoReserva.EN_REVISION_MANUAL))
                .thenReturn(List.of(primera));
        AlojamientoReservado alojamiento = new AlojamientoReservado(PUBLICACION_ID, "Casa en la playa",
                "Santa Marta", null, Map.of(), "Fotos falsas");
        when(publicaciones.consultarReservados(Set.of(PUBLICACION_ID))).thenReturn(Map.of(PUBLICACION_ID, alojamiento));

        List<ReservaEnRevision> enRevision = servicio.listarEnRevision();

        assertThat(enRevision).containsExactly(new ReservaEnRevision(primera, alojamiento));
    }

    @Test
    @DisplayName("AC3: CONFIRMAR deja la reserva CONFIRMADA, avisa a huésped y anfitrión y lo audita")
    void confirmarLaDejaConfirmada() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(enRevision(TRANSACCION)));
        when(registro.confirmarRevision(RESERVA_ID)).thenReturn(true);

        ResolucionRevision resolucion = servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CONFIRMAR", null);

        assertThat(resolucion.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(resolucion.reembolsado()).isFalse();
        verifyNoInteractions(pasarela);
        verify(notificaciones).notificar(argThat(reserva -> RESERVA_ID.equals(reserva.getId())),
                eq(TipoNotificacion.RESERVA_CONFIRMADA));
        EventoCritico evento = auditado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.ACCION_ADMINISTRATIVA);
        assertThat(evento.usuario()).isEqualTo(String.valueOf(ADMINISTRADOR_ID));
        assertThat(evento.datos()).containsEntry("operacion", "resolver_revision")
                .containsEntry("decision", "CONFIRMAR").containsEntry("reembolsado", false);
    }

    @Test
    @DisplayName("AC3/S7: CANCELAR devuelve el cobro en la pasarela antes de cancelar, con devolución del total")
    void cancelarReembolsaYCancela() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(enRevision(TRANSACCION)));
        when(registro.cancelarRevision(any(Cancelacion.class), anyBoolean())).thenReturn(true);

        ResolucionRevision resolucion = servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CANCELAR",
                "  Publicación engañosa  ");

        var orden = inOrder(pasarela, registro);
        orden.verify(pasarela).reembolsar(TRANSACCION);
        ArgumentCaptor<Cancelacion> cancelacion = ArgumentCaptor.forClass(Cancelacion.class);
        orden.verify(registro).cancelarRevision(cancelacion.capture(), eq(true));
        assertThat(cancelacion.getValue().getReservaId()).isEqualTo(RESERVA_ID);
        assertThat(cancelacion.getValue().getUsuarioId()).isEqualTo(ADMINISTRADOR_ID);
        assertThat(cancelacion.getValue().getMotivo()).isEqualTo("Publicación engañosa");
        assertThat(cancelacion.getValue().getValorDevolucion()).isEqualByComparingTo("470000");
        assertThat(cancelacion.getValue().getEstadoFinal()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(cancelacion.getValue().getFecha()).isEqualTo(AHORA);
        assertThat(resolucion.estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(resolucion.reembolsado()).isTrue();
        assertThat(resolucion.valorDevolucion()).isEqualByComparingTo("470000");
        verify(notificaciones).notificar(argThat(reserva -> RESERVA_ID.equals(reserva.getId())),
                eq(TipoNotificacion.RESERVA_CANCELADA));
        assertThat(auditado().datos()).containsEntry("decision", "CANCELAR").containsEntry("reembolsado", true)
                .containsEntry("motivo", "Publicación engañosa");
    }

    @Test
    @DisplayName("S7: si la pasarela falla, responde 502, la reserva sigue en revisión y nadie es notificado")
    void siLaPasarelaFallaNoCambiaNada() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(enRevision(TRANSACCION)));
        doThrow(new FallaPasarelaException(false, "Stripe caído", null)).when(pasarela).reembolsar(TRANSACCION);

        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CANCELAR", "Motivo"))
                .isInstanceOfSatisfying(ReembolsoFallidoException.class,
                        error -> assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_GATEWAY))
                .hasMessage("No se pudo procesar la devolución en la pasarela de pagos. Intenta de nuevo.");

        verify(registro, never()).cancelarRevision(any(), anyBoolean());
        verify(notificaciones, never()).notificar(any(), any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("S7: sin transacción registrada, cancela sin llamar a la pasarela y sin marcar el pago reembolsado")
    void sinTransaccionNoLlamaALaPasarela() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(enRevision(null)));
        when(registro.cancelarRevision(any(Cancelacion.class), anyBoolean())).thenReturn(true);

        ResolucionRevision resolucion = servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CANCELAR", "Motivo");

        verifyNoInteractions(pasarela);
        verify(registro).cancelarRevision(any(Cancelacion.class), eq(false));
        assertThat(resolucion.reembolsado()).isFalse();
    }

    @Test
    @DisplayName("Si la base falla después de devolver el cobro, el error sube y no se notifica")
    void siLaBaseFallaTrasElReembolsoElErrorSube() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(enRevision(TRANSACCION)));
        when(registro.cancelarRevision(any(Cancelacion.class), anyBoolean()))
                .thenThrow(new DataAccessResourceFailureException("sin conexión"));

        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CANCELAR", "Motivo"))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(pasarela).reembolsar(TRANSACCION);
        verify(notificaciones, never()).notificar(any(), any());
    }

    @Test
    @DisplayName("Resolver una reserva que no está en revisión manual responde 409 sin llamar a la pasarela")
    void resolverOtroEstadoResponde409() {
        Reserva confirmada = enRevision(TRANSACCION);
        ReflectionTestUtils.setField(confirmada, "estado", EstadoReserva.CONFIRMADA);
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(confirmada));

        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CANCELAR", "Motivo"))
                .isInstanceOf(ReservaNoResolubleException.class)
                .hasMessage("Solo se puede resolver una reserva en revisión manual.");
        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CONFIRMAR", null))
                .isInstanceOf(ReservaNoResolubleException.class);

        verifyNoInteractions(pasarela);
        verify(registro, never()).confirmarRevision(any());
    }

    @Test
    @DisplayName("Si otra resolución ganó la carrera, responde 409 y no notifica")
    void siPierdeLaCarreraResponde409() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(enRevision(TRANSACCION)));
        when(registro.confirmarRevision(RESERVA_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CONFIRMAR", null))
                .isInstanceOf(ReservaNoResolubleException.class);

        verify(notificaciones, never()).notificar(any(), any());
    }

    @Test
    @DisplayName("CANCELAR sin motivo responde 400 con el error del campo y no toca nada")
    void cancelarSinMotivoResponde400() {
        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CANCELAR", "  "))
                .isInstanceOfSatisfying(ResolucionInvalidaException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getPropiedades()).containsEntry("errores",
                            Map.of("motivo", "El motivo es obligatorio para cancelar."));
                });

        verifyNoInteractions(pasarela, registro);
    }

    @Test
    @DisplayName("Resolver una reserva que no existe responde 404")
    void resolverInexistenteResponde404() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.resolver(RESERVA_ID, ADMINISTRADOR_ID, "CONFIRMAR", null))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }

    private EventoCritico auditado() {
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        return evento.getValue();
    }

    private static Reserva enRevision(String transaccionId) {
        Reserva reserva = Reserva.nueva(PUBLICACION_ID, 7L, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14), 4,
                new BigDecimal("400000"), new BigDecimal("30000"), new BigDecimal("40000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("470000"), Set.of(), AHORA.plusSeconds(600));
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        ReflectionTestUtils.setField(reserva, "estado", EstadoReserva.EN_REVISION_MANUAL);
        ReflectionTestUtils.setField(reserva, "estadoPago",
                transaccionId == null ? EstadoPago.NO_COBRADO : EstadoPago.APROBADO);
        ReflectionTestUtils.setField(reserva, "transaccionId", transaccionId);
        return reserva;
    }
}
