package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reservas de una publicación bloqueada que pasan a revisión manual (SOF-83, AC2). */
class RevisionManualReservasTest {

    private static final Long PUBLICACION_ID = 30L;
    // 22:00 del 1 de diciembre en Bogotá, ya 2 de diciembre en UTC.
    private static final Instant AHORA = Instant.parse("2026-12-02T03:00:00Z");

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final RevisionManualReservas revision = new RevisionManualReservas(reservas,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Envía a revisión las CONFIRMADA cuya salida es posterior a hoy en Colombia y devuelve cuántas fueron")
    void enviaARevisionConElHoyDeColombia() {
        when(reservas.enviarARevision(PUBLICACION_ID, LocalDate.of(2026, 12, 1))).thenReturn(2);

        int enviadas = revision.enviarARevision(PUBLICACION_ID);

        assertThat(enviadas).isEqualTo(2);
        verify(reservas).enviarARevision(PUBLICACION_ID, LocalDate.of(2026, 12, 1));
    }
}
