package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoCancelableException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cancelación de una reserva CONFIRMADA por su huésped titular o por el anfitrión de la publicación
 * (SOF-75, HU-30, RF-46/RF-48).
 */
class CancelacionReservaServiceTest {

    private static final Long RESERVA_ID = 42L;
    private static final Long HUESPED_ID = 7L;
    private static final Long ANFITRION_ID = 9L;
    private static final Long OTRO_USUARIO_ID = 55L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 10);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 14);

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final RegistroReservas registro = mock(RegistroReservas.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final CancelacionReservaService servicio = new CancelacionReservaService(reservas, publicaciones,
            registro, publicador, Clock.fixed(AHORA, ZoneOffset.UTC));

    @BeforeEach
    void preparar() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(confirmada()));
        when(publicaciones.consultarAcceso(PUBLICACION_ID))
                .thenReturn(Optional.of(new AccesoPublicacion(ANFITRION_ID, true)));
    }

    @Test
    @DisplayName("AC1: el huésped titular cancela su reserva CONFIRMADA y queda la auditoría con el desglose")
    void elHuespedCancelaSuReserva() {
        when(registro.cancelar(any(Cancelacion.class))).thenReturn(true);

        Cancelacion cancelacion = servicio.cancelar(RESERVA_ID, HUESPED_ID, "Cambio de planes");

        assertThat(cancelacion.getReservaId()).isEqualTo(RESERVA_ID);
        assertThat(cancelacion.getUsuarioId()).isEqualTo(HUESPED_ID);
        assertThat(cancelacion.getMotivo()).isEqualTo("Cambio de planes");
        assertThat(cancelacion.getValorDevolucion()).isEqualByComparingTo("470000");
        assertThat(cancelacion.getEstadoFinal()).isEqualTo(EstadoReserva.CANCELADA);
        verify(registro).cancelar(argThat(registrada -> registrada.getReservaId().equals(RESERVA_ID)
                && registrada.getUsuarioId().equals(HUESPED_ID)));
        assertThat(auditado().resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(auditado().datos()).containsEntry("canceladoPor", "HUESPED");
    }

    @Test
    @DisplayName("AC3: el anfitrión también puede cancelar, con devolución del 100% y la auditoría distingue quién canceló")
    void elAnfitrionCancelaLaReserva() {
        when(registro.cancelar(any(Cancelacion.class))).thenReturn(true);

        Cancelacion cancelacion = servicio.cancelar(RESERVA_ID, ANFITRION_ID, "El alojamiento no estará disponible");

        assertThat(cancelacion.getValorDevolucion()).isEqualByComparingTo("470000");
        assertThat(auditado().datos()).containsEntry("canceladoPor", "ANFITRION");
    }

    @Test
    @DisplayName("AC4: si la estancia ya terminó, no se puede cancelar y lo informa")
    void laEstanciaYaTerminadaNoSePuedeCancelar() {
        when(reservas.findById(RESERVA_ID))
                .thenReturn(Optional.of(confirmada(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5))));

        assertThatThrownBy(() -> servicio.cancelar(RESERVA_ID, HUESPED_ID, "Motivo"))
                .isInstanceOf(ReservaNoCancelableException.class)
                .hasMessage("La estancia ya terminó: no se puede cancelar.");

        verify(registro, never()).cancelar(any());
    }

    @Test
    @DisplayName("Una reserva que ya está cancelada no se puede volver a cancelar")
    void unaReservaYaCanceladaNoSeVuelveACancelar() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(con(EstadoReserva.CANCELADA)));

        assertThatThrownBy(() -> servicio.cancelar(RESERVA_ID, HUESPED_ID, "Motivo"))
                .isInstanceOf(ReservaNoCancelableException.class)
                .hasMessage("Esta reserva ya está cancelada.");

        verify(registro, never()).cancelar(any());
    }

    @Test
    @DisplayName("Una reserva PENDIENTE (todavía sin pago) no se puede cancelar")
    void unaReservaPendienteNoSePuedeCancelar() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(con(EstadoReserva.PENDIENTE)));

        assertThatThrownBy(() -> servicio.cancelar(RESERVA_ID, HUESPED_ID, "Motivo"))
                .isInstanceOf(ReservaNoCancelableException.class)
                .hasMessage("Esta reserva todavía no está confirmada: no se puede cancelar.");
    }

    @Test
    @DisplayName("AC5: quien no es ni el huésped ni el anfitrión recibe 403 y el intento queda auditado como fallo")
    void unUsuarioSinNingunaPropiedadRecibe403() {
        assertThatThrownBy(() -> servicio.cancelar(RESERVA_ID, OTRO_USUARIO_ID, "Motivo"))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(registro, never()).cancelar(any());
        assertThat(auditado().resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(auditado().datos()).containsEntry("resultado", "intento_no_autorizado");
    }

    @Test
    @DisplayName("Si la actualización condicional pierde la carrera (0 filas), no inserta la cancelación y responde 409")
    void siPierdeLaCarreraNoInsertaLaCancelacion() {
        when(registro.cancelar(any(Cancelacion.class))).thenReturn(false);
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(confirmada()),
                Optional.of(con(EstadoReserva.VENCIDA)));

        assertThatThrownBy(() -> servicio.cancelar(RESERVA_ID, HUESPED_ID, "Motivo"))
                .isInstanceOf(ReservaNoCancelableException.class)
                .hasMessage("Esta reserva ya venció: no se puede cancelar.");

    }

    @Test
    @DisplayName("Una reserva que no existe responde 404")
    void unaReservaInexistenteResponde404() {
        when(reservas.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelar(999L, HUESPED_ID, "Motivo"))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }

    private EventoCritico auditado() {
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        return evento.getValue();
    }

    private static Reserva confirmada() {
        return confirmada(LLEGADA, SALIDA);
    }

    private static Reserva confirmada(LocalDate llegada, LocalDate salida) {
        return con(EstadoReserva.CONFIRMADA, llegada, salida);
    }

    private static Reserva con(EstadoReserva estado) {
        return con(estado, LLEGADA, SALIDA);
    }

    private static Reserva con(EstadoReserva estado, LocalDate llegada, LocalDate salida) {
        Reserva reserva = Reserva.nueva(PUBLICACION_ID, HUESPED_ID, llegada, salida, 4, new BigDecimal("400000"),
                new BigDecimal("30000"), new BigDecimal("40000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("470000"), Set.of(), AHORA.plusSeconds(600));
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }
}
