package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
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
import org.springframework.test.util.ReflectionTestUtils;

/** Separación en futuras y pasadas y estado actual del historial (SOF-35), con el reloj fijo. */
class HistorialReservasServiceTest {

    // 2 de diciembre a las 03:00 UTC: en Bogotá todavía es el 1 de diciembre a las 22:00.
    private static final Instant AHORA = Instant.parse("2026-12-02T03:00:00Z");
    private static final LocalDate HOY_BOGOTA = LocalDate.of(2026, 12, 1);

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final HistorialReservasService servicio = new HistorialReservasService(reservas, publicaciones,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("El día se cuenta en Bogotá: la que sale hoy allá es futura y la que salió ayer es pasada")
    void separaPorElDiaDeBogota() {
        Reserva salioAyer = reserva(1L, 10L, HOY_BOGOTA.minusDays(3), HOY_BOGOTA.minusDays(1), EstadoReserva.CONFIRMADA,
                AHORA.minusSeconds(86_400));
        Reserva saleHoy = reserva(2L, 10L, HOY_BOGOTA.minusDays(2), HOY_BOGOTA, EstadoReserva.CONFIRMADA,
                AHORA.minusSeconds(86_400));
        when(reservas.findByHuespedIdOrderByLlegadaAscIdAsc(7L)).thenReturn(List.of(salioAyer, saleHoy));
        when(publicaciones.consultarReservados(any())).thenReturn(Map.of(10L, alojamiento(10L)));

        HistorialReservas historial = servicio.mias(7L);

        assertThat(historial.futuras()).extracting(c -> c.reserva().getId()).containsExactly(2L);
        assertThat(historial.pasadas()).extracting(c -> c.reserva().getId()).containsExactly(1L);
    }

    @Test
    @DisplayName("Las pasadas van de la más reciente a la más antigua y las futuras de la más próxima a la más lejana")
    void ordenaCadaLista() {
        Reserva antigua = reserva(1L, 10L, HOY_BOGOTA.minusDays(30), HOY_BOGOTA.minusDays(28), EstadoReserva.CONFIRMADA,
                AHORA);
        Reserva reciente = reserva(2L, 10L, HOY_BOGOTA.minusDays(10), HOY_BOGOTA.minusDays(8), EstadoReserva.CANCELADA,
                AHORA);
        Reserva proxima = reserva(3L, 10L, HOY_BOGOTA.plusDays(5), HOY_BOGOTA.plusDays(7), EstadoReserva.CONFIRMADA,
                AHORA);
        Reserva lejana = reserva(4L, 10L, HOY_BOGOTA.plusDays(40), HOY_BOGOTA.plusDays(42), EstadoReserva.CONFIRMADA,
                AHORA);
        when(reservas.findByHuespedIdOrderByLlegadaAscIdAsc(7L)).thenReturn(List.of(antigua, reciente, proxima, lejana));
        when(publicaciones.consultarReservados(any())).thenReturn(Map.of(10L, alojamiento(10L)));

        HistorialReservas historial = servicio.mias(7L);

        assertThat(historial.futuras()).extracting(c -> c.reserva().getId()).containsExactly(3L, 4L);
        assertThat(historial.pasadas()).extracting(c -> c.reserva().getId()).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("Una PENDIENTE con el apartado agotado se ve VENCIDA; una con el apartado vigente sigue PENDIENTE")
    void elEstadoActualConsideraElApartado() {
        Reserva agotada = reserva(1L, 10L, HOY_BOGOTA.plusDays(5), HOY_BOGOTA.plusDays(7), EstadoReserva.PENDIENTE, AHORA);
        Reserva vigente = reserva(2L, 10L, HOY_BOGOTA.plusDays(9), HOY_BOGOTA.plusDays(11), EstadoReserva.PENDIENTE,
                AHORA.plusSeconds(60));
        when(reservas.findByHuespedIdOrderByLlegadaAscIdAsc(7L)).thenReturn(List.of(agotada, vigente));
        when(publicaciones.consultarReservados(any())).thenReturn(Map.of(10L, alojamiento(10L)));

        HistorialReservas historial = servicio.mias(7L);

        assertThat(historial.futuras()).extracting(ReservaConsultada::estadoActual)
                .containsExactly(EstadoReserva.VENCIDA, EstadoReserva.PENDIENTE);
    }

    @Test
    @DisplayName("Si la publicación ya no existe, la reserva aparece igual, sin alojamiento")
    void unaPublicacionInexistenteNoEscondeLaReserva() {
        Reserva huerfana = reserva(1L, 99L, HOY_BOGOTA.plusDays(5), HOY_BOGOTA.plusDays(7), EstadoReserva.CONFIRMADA,
                AHORA);
        when(reservas.findByHuespedIdOrderByLlegadaAscIdAsc(7L)).thenReturn(List.of(huerfana));
        when(publicaciones.consultarReservados(Set.of(99L))).thenReturn(Map.of());

        HistorialReservas historial = servicio.mias(7L);

        assertThat(historial.futuras()).singleElement().satisfies(consultada -> {
            assertThat(consultada.reserva().getId()).isEqualTo(1L);
            assertThat(consultada.alojamiento()).isNull();
        });
    }

    @Test
    @DisplayName("El detalle de una reserva inexistente es 404 y el de una ajena 403, sin leer la publicación")
    void elDetalleExigeQueLaReservaExistaYSeaDelHuesped() {
        Reserva deOtro = reserva(1L, 10L, HOY_BOGOTA.plusDays(5), HOY_BOGOTA.plusDays(7), EstadoReserva.CONFIRMADA,
                AHORA);
        when(reservas.findById(1L)).thenReturn(Optional.of(deOtro));
        when(reservas.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(2L, 7L)).isInstanceOf(ReservaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.detalle(1L, 8L)).isInstanceOf(AccesoDenegadoException.class);
        verify(publicaciones, never()).consultarReservados(any());
    }

    @Test
    @DisplayName("El detalle propio trae el estado actual y el alojamiento de la reserva")
    void elDetallePropioTraeEstadoYAlojamiento() {
        Reserva propia = reserva(1L, 10L, HOY_BOGOTA.plusDays(5), HOY_BOGOTA.plusDays(7), EstadoReserva.PENDIENTE,
                AHORA);
        when(reservas.findById(1L)).thenReturn(Optional.of(propia));
        when(publicaciones.consultarReservados(Set.of(10L))).thenReturn(Map.of(10L, alojamiento(10L)));

        ReservaConsultada detalle = servicio.detalle(1L, 7L);

        assertThat(detalle.estadoActual()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(detalle.alojamiento().titulo()).isEqualTo("Casa en la playa");
    }

    private static AlojamientoReservado alojamiento(Long id) {
        return new AlojamientoReservado(id, "Casa en la playa", "Santa Marta", null, Map.of(), null);
    }

    private static Reserva reserva(Long id, Long publicacionId, LocalDate llegada, LocalDate salida,
            EstadoReserva estado, Instant venceEn) {
        Reserva reserva = Reserva.nueva(publicacionId, 7L, llegada, salida, 2, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("220000"), Set.of(), venceEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }
}
