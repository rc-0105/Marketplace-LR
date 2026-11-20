package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Adaptador de los puertos de ocupación de disponibilidad (SOF-33): ocupan noches las CONFIRMADA y las
 * PENDIENTE cuyo apartado sigue vigente (ADR-4); el repositorio ya filtra con el instante que le da el reloj.
 */
class ConsultaOcupacionReservasTest {

    private static final Long PUBLICACION_ID = 30L;
    private static final LocalDate DESDE = LocalDate.of(2026, 12, 10);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 14);
    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final ConsultaOcupacionReservas servicio = new ConsultaOcupacionReservas(reservas,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Los ocupantes entre dos fechas son las reservas vigentes que cruzan la ventana")
    void ocupantesEntreMapeaLasReservasQueCruzan() {
        when(reservas.findOcupantes(PUBLICACION_ID, DESDE, HASTA, AHORA))
                .thenReturn(List.of(reservaDe(5L, DESDE, HASTA)));

        List<ReservaOcupada> ocupantes = servicio.ocupantesEntre(PUBLICACION_ID, DESDE, HASTA);

        assertThat(ocupantes).containsExactly(new ReservaOcupada(5L, DESDE, HASTA));
    }

    @Test
    @DisplayName("Un periodo queda bloqueado por cualquier reserva que ocupa noches (CONFIRMADA o PENDIENTE vigente)")
    void reservaConfirmadaEnConsideraLasQueOcupan() {
        when(reservas.findOcupantes(PUBLICACION_ID, DESDE, HASTA, AHORA))
                .thenReturn(List.of(reservaDe(9L, DESDE, HASTA)));

        assertThat(servicio.reservaConfirmadaEn(PUBLICACION_ID, DESDE, HASTA)).contains(9L);
    }

    @Test
    @DisplayName("Sin reservas que ocupen, el periodo no está bloqueado")
    void sinOcupantesElPeriodoNoEstaBloqueado() {
        when(reservas.findOcupantes(PUBLICACION_ID, DESDE, HASTA, AHORA)).thenReturn(List.of());

        assertThat(servicio.reservaConfirmadaEn(PUBLICACION_ID, DESDE, HASTA)).isEmpty();
    }

    @Test
    @DisplayName("Sin publicaciones, publicacionesOcupadas no consulta el repositorio")
    void publicacionesOcupadasSinPublicacionesNoConsulta() {
        assertThat(servicio.publicacionesOcupadas(List.of(), DESDE, HASTA)).isEmpty();

        verify(reservas, never()).findOcupantesDeVarias(anyCollection(), any(), any(), any());
    }

    @Test
    @DisplayName("Con publicaciones, publicacionesOcupadas hace una sola consulta por lote")
    void publicacionesOcupadasConsultaUnaSolaVez() {
        when(reservas.findOcupantesDeVarias(List.of(PUBLICACION_ID, 31L), DESDE, HASTA, AHORA))
                .thenReturn(List.of(reservaDe(5L, DESDE, HASTA)));

        Set<Long> ocupadas = servicio.publicacionesOcupadas(List.of(PUBLICACION_ID, 31L), DESDE, HASTA);

        assertThat(ocupadas).containsExactly(PUBLICACION_ID);
        verify(reservas, never()).findOcupantes(any(), any(), any(), any());
    }

    private static Reserva reservaDe(Long id, LocalDate llegada, LocalDate salida) {
        Reserva reserva = Reserva.nueva(PUBLICACION_ID, 1L, llegada, salida, 4, null, null, null, null, null, null,
                Set.of(), AHORA.plusSeconds(600));
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }
}
