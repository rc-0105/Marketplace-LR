package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Conciliación de los pagos que quedaron en ERROR (SOF-34, S8): la pasarela pudo cobrar aunque no respondió a
 * tiempo, y ese cobro no puede quedar sin reserva ni sin reembolso.
 */
class ConciliacionPagosTest {

    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");
    private static final Instant CREADA = Instant.parse("2026-12-01T14:00:00Z");

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final RegistroReservas registro = mock(RegistroReservas.class);
    private final PasarelaPagos pasarela = mock(PasarelaPagos.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final NotificacionesReserva notificaciones = mock(NotificacionesReserva.class);
    private final ConciliacionPagos conciliacion = new ConciliacionPagos(reservas, registro, pasarela, publicador,
            notificaciones, Clock.fixed(AHORA, ZoneOffset.UTC), 60);

    @Test
    @DisplayName("Si la pasarela sí cobró y la reserva sigue PENDIENTE, la confirma con esa transacción")
    void unCobroDeUnaPendienteLaConfirma() {
        Reserva reserva = conError(1L, EstadoReserva.PENDIENTE, AHORA.plusSeconds(300));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(reserva));
        when(pasarela.buscarCobroAprobado(referencia(1L))).thenReturn(Optional.of("pi_1"));
        when(registro.confirmarPago(1L, "pi_1")).thenReturn(true);

        conciliacion.conciliar();

        verify(registro).confirmarPago(1L, "pi_1");
        verify(pasarela, never()).reembolsar(anyString());
        assertThat(auditado().datos()).containsEntry("resultado", "confirmado");
        // SOF-37: la confirmación por conciliación también avisa al huésped y al anfitrión.
        verify(notificaciones).notificar(reserva, TipoNotificacion.RESERVA_CONFIRMADA);
    }

    @Test
    @DisplayName("Si la pasarela sí cobró pero la reserva ya venció, reembolsa el cobro y lo registra como REEMBOLSADO")
    void unCobroDeUnaVencidaSeReembolsa() {
        Reserva reserva = conError(2L, EstadoReserva.VENCIDA, AHORA.minusSeconds(300));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(reserva));
        when(pasarela.buscarCobroAprobado(referencia(2L))).thenReturn(Optional.of("pi_2"));
        when(registro.confirmarPago(2L, "pi_2")).thenReturn(false);
        when(reservas.findById(2L)).thenReturn(Optional.of(reserva));

        conciliacion.conciliar();

        verify(pasarela).reembolsar("pi_2");
        verify(registro).marcarReembolsado(2L, "pi_2");
        assertThat(auditado().datos()).containsEntry("resultado", "reembolsado");
        verify(notificaciones, never()).notificar(any(), any());
    }

    @Test
    @DisplayName("Si el reembolso falla, la reserva sigue en ERROR para reintentarlo en la próxima corrida")
    void unReembolsoFallidoSeReintentaLuego() {
        Reserva reserva = conError(2L, EstadoReserva.VENCIDA, AHORA.minusSeconds(300));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(reserva));
        when(pasarela.buscarCobroAprobado(referencia(2L))).thenReturn(Optional.of("pi_2"));
        when(reservas.findById(2L)).thenReturn(Optional.of(reserva));
        doThrow(new FallaPasarelaException(true, "Stripe no respondió", null)).when(pasarela).reembolsar("pi_2");

        conciliacion.conciliar();

        verify(registro, never()).marcarReembolsado(any(), any());
        verify(registro, never()).marcarNoCobrado(any());
    }

    @Test
    @DisplayName("Si otro camino ya confirmó la reserva con esa misma transacción, no reembolsa nada")
    void unaYaConfirmadaConLaMismaTransaccionNoSeReembolsa() {
        Reserva reserva = conError(3L, EstadoReserva.PENDIENTE, AHORA.plusSeconds(300));
        Reserva confirmada = conError(3L, EstadoReserva.CONFIRMADA, AHORA.plusSeconds(300));
        ReflectionTestUtils.setField(confirmada, "estadoPago", EstadoPago.APROBADO);
        ReflectionTestUtils.setField(confirmada, "transaccionId", "pi_3");
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(reserva));
        when(pasarela.buscarCobroAprobado(referencia(3L))).thenReturn(Optional.of("pi_3"));
        when(reservas.findById(3L)).thenReturn(Optional.of(confirmada));

        conciliacion.conciliar();

        verify(pasarela, never()).reembolsar(anyString());
        verify(registro, never()).marcarReembolsado(any(), any());
    }

    @Test
    @DisplayName("Sin cobro en la pasarela, espera mientras no pase el apartado más la gracia")
    void sinCobroEsperaLaGracia() {
        Reserva reserva = conError(4L, EstadoReserva.VENCIDA, AHORA.minusSeconds(59 * 60));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(reserva));
        when(pasarela.buscarCobroAprobado(referencia(4L))).thenReturn(Optional.empty());

        conciliacion.conciliar();

        verify(registro, never()).marcarNoCobrado(any());
    }

    @Test
    @DisplayName("Sin cobro en la pasarela pasada la gracia, cierra el pago como NO_COBRADO")
    void sinCobroTrasLaGraciaQuedaNoCobrado() {
        Reserva reserva = conError(5L, EstadoReserva.VENCIDA, AHORA.minusSeconds(61 * 60));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(reserva));
        when(pasarela.buscarCobroAprobado(referencia(5L))).thenReturn(Optional.empty());

        conciliacion.conciliar();

        verify(registro).marcarNoCobrado(5L);
        assertThat(auditado().datos()).containsEntry("resultado", "no_cobrado");
    }

    @Test
    @DisplayName("Si la pasarela no responde con una reserva, sigue con las demás")
    void unaFallaNoDetieneLaCorrida() {
        Reserva primera = conError(6L, EstadoReserva.VENCIDA, AHORA.minusSeconds(300));
        Reserva segunda = conError(7L, EstadoReserva.PENDIENTE, AHORA.plusSeconds(300));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(primera, segunda));
        when(pasarela.buscarCobroAprobado(referencia(6L))).thenThrow(new FallaPasarelaException(true, "sin respuesta", null));
        when(pasarela.buscarCobroAprobado(referencia(7L))).thenReturn(Optional.of("pi_7"));
        when(registro.confirmarPago(7L, "pi_7")).thenReturn(true);

        conciliacion.conciliar();

        verify(registro).confirmarPago(7L, "pi_7");
    }

    @Test
    @DisplayName("Un error inesperado con una reserva (p. ej. de la base de datos) no detiene la corrida")
    void unErrorInesperadoNoDetieneLaCorrida() {
        Reserva primera = conError(8L, EstadoReserva.PENDIENTE, AHORA.plusSeconds(300));
        Reserva segunda = conError(9L, EstadoReserva.PENDIENTE, AHORA.plusSeconds(300));
        when(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).thenReturn(List.of(primera, segunda));
        when(pasarela.buscarCobroAprobado(referencia(8L))).thenReturn(Optional.of("pi_8"));
        when(registro.confirmarPago(8L, "pi_8")).thenThrow(new IllegalStateException("violación de unicidad"));
        when(pasarela.buscarCobroAprobado(referencia(9L))).thenReturn(Optional.of("pi_9"));
        when(registro.confirmarPago(9L, "pi_9")).thenReturn(true);

        conciliacion.conciliar();

        verify(registro).confirmarPago(9L, "pi_9");
    }

    @Test
    @DisplayName("Corre cada pagos.conciliacion.intervalo-minutos (5 por defecto)")
    void seProgramaSegunLaConfiguracion() throws NoSuchMethodException {
        Scheduled programacion = ConciliacionPagos.class.getMethod("conciliar").getAnnotation(Scheduled.class);

        assertThat(programacion.timeUnit()).isEqualTo(TimeUnit.MINUTES);
        assertThat(programacion.fixedDelayString()).isEqualTo("${pagos.conciliacion.intervalo-minutos:5}");
        assertThat(programacion.initialDelayString()).isEqualTo("${pagos.conciliacion.intervalo-minutos:5}");
    }

    private EventoCritico auditado() {
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        return evento.getValue();
    }

    // Igual que Reserva.referenciaPago(): id más instante de creación.
    private static String referencia(Long id) {
        return "reserva-" + id + "-" + CREADA.toEpochMilli();
    }

    private static Reserva conError(Long id, EstadoReserva estado, Instant venceEn) {
        Reserva reserva = Reserva.nueva(30L, 7L, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14), 4,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("470000"), Set.of(), venceEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "creadoEn", CREADA);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        ReflectionTestUtils.setField(reserva, "estadoPago", EstadoPago.ERROR);
        return reserva;
    }
}
