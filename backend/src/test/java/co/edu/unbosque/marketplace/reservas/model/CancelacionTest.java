package co.edu.unbosque.marketplace.reservas.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cancelación registrada de una reserva (SOF-75): guarda quién canceló, cuándo, el motivo y la devolución. */
class CancelacionTest {

    @Test
    @DisplayName("Una cancelación nueva guarda el desglose con el que se registró")
    void unaCancelacionNuevaGuardaElDesglose() {
        Instant fecha = Instant.parse("2026-12-01T15:00:00Z");

        Cancelacion cancelacion = Cancelacion.nueva(42L, 7L, fecha, "Cambio de planes", new BigDecimal("470000"),
                EstadoReserva.CANCELADA);

        assertThat(cancelacion.getReservaId()).isEqualTo(42L);
        assertThat(cancelacion.getUsuarioId()).isEqualTo(7L);
        assertThat(cancelacion.getFecha()).isEqualTo(fecha);
        assertThat(cancelacion.getMotivo()).isEqualTo("Cambio de planes");
        assertThat(cancelacion.getValorDevolucion()).isEqualByComparingTo("470000");
        assertThat(cancelacion.getEstadoFinal()).isEqualTo(EstadoReserva.CANCELADA);
    }
}
