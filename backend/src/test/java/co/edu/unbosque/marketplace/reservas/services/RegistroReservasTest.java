package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.reservas.model.Reserva;
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
    private final RegistroReservas registro = new RegistroReservas(reservas, Clock.fixed(AHORA, ZoneOffset.UTC));

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

    private static Reserva nueva() {
        return Reserva.nueva(30L, 7L, LLEGADA, SALIDA, 4, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, Set.of(), AHORA.plusSeconds(900));
    }
}
