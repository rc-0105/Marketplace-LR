package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Adaptador de los puertos de ocupación de disponibilidad (SOF-33): PENDIENTE y CONFIRMADA ocupan noches. */
class ConsultaOcupacionReservasTest {

    private static final Long PUBLICACION_ID = 30L;
    private static final LocalDate DESDE = LocalDate.of(2026, 12, 10);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 14);

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final ConsultaOcupacionReservas servicio = new ConsultaOcupacionReservas(reservas);

    @Test
    @DisplayName("Los ocupantes entre dos fechas son las reservas PENDIENTE o CONFIRMADA que cruzan la ventana")
    void ocupantesEntreMapeaLasReservasQueCruzan() {
        Reserva reserva = reservaDe(5L, DESDE, HASTA);
        when(reservas.findByPublicacionIdAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(eq(PUBLICACION_ID),
                eq(Set.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)), eq(HASTA), eq(DESDE)))
                .thenReturn(List.of(reserva));

        List<ReservaOcupada> ocupantes = servicio.ocupantesEntre(PUBLICACION_ID, DESDE, HASTA);

        assertThat(ocupantes).containsExactly(new ReservaOcupada(5L, DESDE, HASTA));
    }

    @Test
    @DisplayName("Solo una reserva CONFIRMADA bloquea el periodo; sin ninguna, devuelve vacío")
    void reservaConfirmadaEnSoloPreguntaPorConfirmadas() {
        when(reservas.findFirstByPublicacionIdAndEstadoAndLlegadaLessThanAndSalidaGreaterThan(PUBLICACION_ID,
                EstadoReserva.CONFIRMADA, HASTA, DESDE)).thenReturn(Optional.of(reservaDe(9L, DESDE, HASTA)));

        assertThat(servicio.reservaConfirmadaEn(PUBLICACION_ID, DESDE, HASTA)).contains(9L);
        verify(reservas).findFirstByPublicacionIdAndEstadoAndLlegadaLessThanAndSalidaGreaterThan(PUBLICACION_ID,
                EstadoReserva.CONFIRMADA, HASTA, DESDE);
    }

    @Test
    @DisplayName("Sin publicaciones, publicacionesOcupadas no consulta el repositorio")
    void publicacionesOcupadasSinPublicacionesNoConsulta() {
        assertThat(servicio.publicacionesOcupadas(List.of(), DESDE, HASTA)).isEmpty();

        verify(reservas, never()).findByPublicacionIdInAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(
                anyCollection(), anyCollection(), any(), any());
    }

    @Test
    @DisplayName("Con publicaciones, publicacionesOcupadas hace una sola consulta por lote")
    void publicacionesOcupadasConsultaUnaSolaVez() {
        when(reservas.findByPublicacionIdInAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(
                eq(List.of(PUBLICACION_ID, 31L)), eq(Set.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)),
                eq(HASTA), eq(DESDE))).thenReturn(List.of(reservaDe(5L, DESDE, HASTA)));

        Set<Long> ocupadas = servicio.publicacionesOcupadas(List.of(PUBLICACION_ID, 31L), DESDE, HASTA);

        assertThat(ocupadas).containsExactly(PUBLICACION_ID);
        verify(reservas, never()).findByPublicacionIdAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(any(), any(),
                any(), any());
    }

    private static Reserva reservaDe(Long id, LocalDate llegada, LocalDate salida) {
        Reserva reserva = Reserva.nueva(PUBLICACION_ID, 1L, llegada, salida, 4, null, null, null, null, null, null);
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }
}
