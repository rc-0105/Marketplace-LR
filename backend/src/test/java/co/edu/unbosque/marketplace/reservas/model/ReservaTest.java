package co.edu.unbosque.marketplace.reservas.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Reserva sobre una cotización vigente (SOF-33, ADR-4): nace siempre PENDIENTE. */
class ReservaTest {

    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 10);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 14);
    private static final Instant VENCE = Instant.parse("2026-12-01T15:15:00Z");

    @Test
    @DisplayName("Una reserva nueva queda PENDIENTE con el desglose de la cotización que la originó")
    void unaReservaNuevaQuedaPendienteConElDesglose() {
        Reserva reserva = Reserva.nueva(30L, 7L, LLEGADA, SALIDA, 4, new BigDecimal("400000"),
                new BigDecimal("30000"), new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("15000"),
                new BigDecimal("485000"), Set.of(9L, 4L), VENCE);

        assertThat(reserva.getPublicacionId()).isEqualTo(30L);
        assertThat(reserva.getHuespedId()).isEqualTo(7L);
        assertThat(reserva.getLlegada()).isEqualTo(LLEGADA);
        assertThat(reserva.getSalida()).isEqualTo(SALIDA);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(reserva.getNoches()).isEqualTo(4);
        assertThat(reserva.getPrecioBase()).isEqualByComparingTo("400000");
        assertThat(reserva.getTarifaLimpieza()).isEqualByComparingTo("30000");
        assertThat(reserva.getTarifaServicio()).isEqualByComparingTo("40000");
        assertThat(reserva.getDescuento()).isEqualByComparingTo("0");
        assertThat(reserva.getTotalServiciosAdicionales()).isEqualByComparingTo("15000");
        assertThat(reserva.getTotal()).isEqualByComparingTo("485000");
        assertThat(reserva.getServiciosAdicionalesIds()).containsExactlyInAnyOrder(4L, 9L);
    }

    @Test
    @DisplayName("Una PENDIENTE solo ocupa noches hasta que vence su apartado; al vencer pasa a VENCIDA y libera")
    void unaPendienteOcupaSoloMientrasElApartadoVale() {
        Reserva reserva = nueva();

        assertThat(reserva.getVenceEn()).isEqualTo(VENCE);
        assertThat(reserva.ocupaNoches(VENCE.minusSeconds(1))).isTrue();
        assertThat(reserva.ocupaNoches(VENCE)).isFalse();
        assertThat(reserva.ocupaNoches(VENCE.plusSeconds(1))).isFalse();

        reserva.vencer();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(reserva.ocupaNoches(VENCE.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("Una CONFIRMADA ocupa noches aunque haya pasado el tiempo del apartado")
    void unaConfirmadaOcupaSiempre() {
        Reserva reserva = nueva();
        ReflectionTestUtils.setField(reserva, "estado", EstadoReserva.CONFIRMADA);

        assertThat(reserva.ocupaNoches(VENCE.plusSeconds(3600))).isTrue();
    }

    private static Reserva nueva() {
        return Reserva.nueva(30L, 7L, LLEGADA, SALIDA, 4, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, Set.of(), VENCE);
    }
}
