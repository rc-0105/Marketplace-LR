package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.CancelacionRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.dao.DataIntegrityViolationException;

/** Escrituras transaccionales de reservas (SOF-33): liberar apartados vencidos antes de insertar. */
class RegistroReservasTest {

    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 10);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 14);

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final CancelacionRepository cancelaciones = mock(CancelacionRepository.class);
    private final RegistroReservas registro = new RegistroReservas(reservas, cancelaciones,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Antes de insertar libera los apartados vencidos que cruzan las mismas noches de la publicación")
    void liberaLosApartadosVencidosAntesDeInsertar() {
        when(reservas.saveAndFlush(any(Reserva.class))).then(AdditionalAnswers.returnsFirstArg());
        Reserva nueva = nueva();

        assertThat(registro.registrar(nueva)).isSameAs(nueva);

        var orden = inOrder(reservas);
        orden.verify(reservas).vencerCaducadosEn(30L, LLEGADA, SALIDA, AHORA);
        orden.verify(reservas).saveAndFlush(nueva);
    }

    @Test
    @DisplayName("Si la inserción falla, el error de integridad sube tal cual para que la transacción revierta")
    void elErrorDeIntegridadSube() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("cruce");
        when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(error);

        assertThatThrownBy(() -> registro.registrar(nueva())).isSameAs(error);
    }

    @Test
    @DisplayName("vencerCaducados pasa a VENCIDA los apartados con la hora de tope del reloj y devuelve cuántos")
    void vencerCaducadosDelegaEnElRepositorio() {
        when(reservas.vencerCaducados(AHORA)).thenReturn(3);

        assertThat(registro.vencerCaducados()).isEqualTo(3);
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelar cambia el estado y registra la cancelación, en ese orden y en la misma transacción (SOF-75)")
    void cancelarCambiaElEstadoYRegistraLaCancelacion() {
        when(reservas.cancelar(42L)).thenReturn(1);
        Cancelacion cancelacion = cancelacionDe(42L);

        assertThat(registro.cancelar(cancelacion)).isTrue();

        var orden = inOrder(reservas, cancelaciones);
        orden.verify(reservas).cancelar(42L);
        orden.verify(cancelaciones).saveAndFlush(cancelacion);
    }

    @Test
    @DisplayName("Si la reserva ya no estaba CONFIRMADA, cancelar es falso y no registra ninguna cancelación (SOF-75)")
    void cancelarSinCambioNoRegistraLaCancelacion() {
        when(reservas.cancelar(99L)).thenReturn(0);

        assertThat(registro.cancelar(cancelacionDe(99L))).isFalse();

        verify(cancelaciones, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("SOF-83: confirmar una reserva en revisión delega en la actualización condicional")
    void confirmarRevisionDelegaEnElRepositorio() {
        when(reservas.confirmarRevision(42L)).thenReturn(1, 0);

        assertThat(registro.confirmarRevision(42L)).isTrue();
        assertThat(registro.confirmarRevision(42L)).isFalse();
    }

    @Test
    @DisplayName("SOF-83: cancelar una reserva en revisión ya reembolsada cambia estado y pago, y registra la cancelación")
    void cancelarRevisionReembolsadaRegistraLaCancelacion() {
        when(reservas.cancelarRevisionReembolsada(42L)).thenReturn(1);
        Cancelacion cancelacion = cancelacionDe(42L);

        assertThat(registro.cancelarRevision(cancelacion, true)).isTrue();

        var orden = inOrder(reservas, cancelaciones);
        orden.verify(reservas).cancelarRevisionReembolsada(42L);
        orden.verify(cancelaciones).saveAndFlush(cancelacion);
        verify(reservas, never()).cancelarRevision(any());
    }

    @Test
    @DisplayName("SOF-83: sin cobro que devolver, cancela la reserva en revisión sin tocar el estado del pago")
    void cancelarRevisionSinReembolsoNoTocaElPago() {
        when(reservas.cancelarRevision(42L)).thenReturn(1);

        assertThat(registro.cancelarRevision(cancelacionDe(42L), false)).isTrue();

        verify(reservas, never()).cancelarRevisionReembolsada(any());
        verify(cancelaciones).saveAndFlush(any());
    }

    @Test
    @DisplayName("SOF-83: si la reserva ya no estaba en revisión, no registra la cancelación")
    void cancelarRevisionSinCambioNoRegistra() {
        when(reservas.cancelarRevision(99L)).thenReturn(0);

        assertThat(registro.cancelarRevision(cancelacionDe(99L), false)).isFalse();

        verify(cancelaciones, never()).saveAndFlush(any());
    }

    private static Cancelacion cancelacionDe(Long reservaId) {
        return Cancelacion.nueva(reservaId, 7L, AHORA, "Motivo", BigDecimal.TEN, EstadoReserva.CANCELADA);
    }

    private static Reserva nueva() {
        return Reserva.nueva(30L, 7L, LLEGADA, SALIDA, 4, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, Set.of(), AHORA.plusSeconds(900));
    }
}
