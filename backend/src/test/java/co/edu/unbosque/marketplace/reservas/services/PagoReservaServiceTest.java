package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.reservas.exceptions.ApartadoPerdidoDuranteElPagoException;
import co.edu.unbosque.marketplace.reservas.exceptions.FalloPasarelaPagoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoPagableException;
import co.edu.unbosque.marketplace.reservas.model.EstadoPago;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import co.edu.unbosque.marketplace.reservas.services.PasarelaPagos.CobroAprobado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Lo que la pasarela simulada no puede provocar en las pruebas de integración (SOF-34): que no responda a
 * tiempo, que falle y que el apartado se pierda mientras cobra.
 */
class PagoReservaServiceTest {

    private static final Long RESERVA_ID = 42L;
    private static final Long HUESPED_ID = 7L;
    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final RegistroReservas registro = mock(RegistroReservas.class);
    private final PasarelaPagos pasarela = mock(PasarelaPagos.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final PagoReservaService servicio = new PagoReservaService(reservas, registro, pasarela, publicador,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private final Logger logger = (Logger) LoggerFactory.getLogger(PagoReservaService.class);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();

    @BeforeEach
    void preparar() {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(pendiente()));
        eventos.start();
        logger.addAppender(eventos);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(eventos);
    }

    @Test
    @DisplayName("AC3: sin respuesta de la pasarela responde 504, deja la reserva PENDIENTE con el pago en ERROR y registra el error")
    void sinRespuestaRegistraElErrorYResponde504() {
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(true, "Stripe no respondió", null));

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa"))
                .isInstanceOfSatisfying(FalloPasarelaPagoException.class, falla -> {
                    assertThat(falla.getEstado()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(falla.getMessage()).isEqualTo("La pasarela de pagos no respondió a tiempo. Tu reserva "
                            + "sigue apartada hasta que venza el apartado: intenta pagar de nuevo.");
                });

        verify(registro).registrarErrorPago(RESERVA_ID);
        verify(registro, never()).confirmarPago(any(), any());
        verify(registro, never()).rechazarPago(any());
        assertThat(eventos.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
            assertThat(evento.getFormattedMessage()).contains("reserva " + RESERVA_ID);
        });
        assertThat(auditado().resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(auditado().datos()).containsEntry("resultado", "sin_respuesta");
    }

    @Test
    @DisplayName("Una falla de la pasarela que no es por tiempo responde 502 y también deja el pago en ERROR")
    void unaFallaDeLaPasarelaResponde502() {
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(false, "Invalid API Key", null));

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa"))
                .isInstanceOfSatisfying(FalloPasarelaPagoException.class, falla -> {
                    assertThat(falla.getEstado()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(falla.getMessage()).isEqualTo("La pasarela de pagos no está disponible en este momento. "
                            + "Tu reserva sigue apartada hasta que venza el apartado: intenta pagar de nuevo.");
                });

        verify(registro).registrarErrorPago(RESERVA_ID);
        assertThat(auditado().datos()).containsEntry("resultado", "falla_pasarela");
    }

    @Test
    @DisplayName("El reintento usa la misma clave de idempotencia, así un cobro que sí pasó no se cobra dos veces")
    void elReintentoUsaLaMismaClave() {
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(true, "Stripe no respondió", null))
                .thenReturn(new CobroAprobado("pi_1"));
        when(registro.confirmarPago(RESERVA_ID, "pi_1")).thenReturn(true);

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa"))
                .isInstanceOf(FalloPasarelaPagoException.class);
        servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa");

        ArgumentCaptor<PasarelaPagos.SolicitudCobro> solicitudes = ArgumentCaptor.forClass(PasarelaPagos.SolicitudCobro.class);
        verify(pasarela, times(2)).cobrar(solicitudes.capture());
        assertThat(solicitudes.getAllValues()).extracting(PasarelaPagos.SolicitudCobro::claveIdempotencia)
                .containsExactly("reserva-42", "reserva-42");
        verify(registro).confirmarPago(RESERVA_ID, "pi_1");
    }

    @Test
    @DisplayName("Tras un tiempo agotado, reintentar con otra tarjeta usa la misma clave: la pasarela no abre un segundo cobro")
    void elReintentoConOtraTarjetaUsaLaMismaClave() {
        when(pasarela.cobrar(any())).thenThrow(new FallaPasarelaException(true, "Stripe no respondió", null))
                .thenReturn(new CobroAprobado("pi_1"));
        when(registro.confirmarPago(RESERVA_ID, "pi_1")).thenReturn(true);

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa"))
                .isInstanceOf(FalloPasarelaPagoException.class);
        servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_mastercard");

        ArgumentCaptor<PasarelaPagos.SolicitudCobro> solicitudes = ArgumentCaptor.forClass(PasarelaPagos.SolicitudCobro.class);
        verify(pasarela, times(2)).cobrar(solicitudes.capture());
        assertThat(solicitudes.getAllValues()).extracting(PasarelaPagos.SolicitudCobro::claveIdempotencia)
                .containsOnly("reserva-42");
    }

    @Test
    @DisplayName("Un rechazo que llega cuando otro intento ya confirmó responde 409 sin decir que las fechas quedaron libres")
    void unRechazoTardioNoAnunciaFechasLibres() {
        when(pasarela.cobrar(any())).thenReturn(new PasarelaPagos.CobroRechazado("card_declined"));
        when(registro.rechazarPago(RESERVA_ID)).thenReturn(false);
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(pendiente()),
                Optional.of(con(EstadoReserva.CONFIRMADA, EstadoPago.APROBADO, "pi_otro")));

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_chargeDeclined"))
                .isInstanceOf(ReservaNoPagableException.class)
                .hasMessage("Esta reserva ya está pagada.");
    }

    @Test
    @DisplayName("Si el apartado se perdió mientras se cobraba, reembolsa el cobro y responde 409")
    void unCobroSinReservaSeReembolsa() {
        when(pasarela.cobrar(any())).thenReturn(new CobroAprobado("pi_tarde"));
        when(registro.confirmarPago(RESERVA_ID, "pi_tarde")).thenReturn(false);
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(pendiente()),
                Optional.of(con(EstadoReserva.VENCIDA, EstadoPago.SIN_PAGO, null)));

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa"))
                .isInstanceOf(ApartadoPerdidoDuranteElPagoException.class)
                .hasMessage("El apartado de la reserva venció mientras se procesaba el pago: el cobro fue reembolsado.");

        verify(pasarela).reembolsar("pi_tarde");
        assertThat(auditado().datos()).containsEntry("resultado", "apartado_perdido").containsEntry("reembolsado", true);
    }

    @Test
    @DisplayName("Si el reembolso falla, responde 409 avisando que quedó pendiente y lo registra para revisión")
    void unReembolsoFallidoQuedaRegistrado() {
        when(pasarela.cobrar(any())).thenReturn(new CobroAprobado("pi_tarde"));
        when(registro.confirmarPago(RESERVA_ID, "pi_tarde")).thenReturn(false);
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(pendiente()),
                Optional.of(con(EstadoReserva.VENCIDA, EstadoPago.SIN_PAGO, null)));
        doThrow(new FallaPasarelaException(true, "Stripe no respondió", null)).when(pasarela).reembolsar("pi_tarde");

        assertThatThrownBy(() -> servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa"))
                .isInstanceOf(ApartadoPerdidoDuranteElPagoException.class)
                .hasMessage("El apartado de la reserva venció mientras se procesaba el pago: "
                        + "el reembolso del cobro quedó pendiente y será revisado.");

        assertThat(eventos.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
            assertThat(evento.getFormattedMessage()).contains("pi_tarde", "revisión manual");
        });
    }

    @Test
    @DisplayName("Si un intento simultáneo ya confirmó con la misma transacción, devuelve la reserva sin reembolsar")
    void unIntentoSimultaneoConLaMismaTransaccionNoReembolsa() {
        when(pasarela.cobrar(any())).thenReturn(new CobroAprobado("pi_compartido"));
        when(registro.confirmarPago(RESERVA_ID, "pi_compartido")).thenReturn(false);
        Reserva confirmada = con(EstadoReserva.CONFIRMADA, EstadoPago.APROBADO, "pi_compartido");
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(pendiente()), Optional.of(confirmada));

        assertThat(servicio.pagar(RESERVA_ID, HUESPED_ID, "pm_card_visa")).isSameAs(confirmada);

        verify(pasarela, never()).reembolsar(anyString());
    }

    private EventoCritico auditado() {
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        return evento.getValue();
    }

    private static Reserva pendiente() {
        return con(EstadoReserva.PENDIENTE, EstadoPago.SIN_PAGO, null);
    }

    private static Reserva con(EstadoReserva estado, EstadoPago estadoPago, String transaccionId) {
        Reserva reserva = Reserva.nueva(30L, HUESPED_ID, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14), 4,
                new BigDecimal("400000"), new BigDecimal("30000"), new BigDecimal("40000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("470000"), Set.of(), AHORA.plusSeconds(600));
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        ReflectionTestUtils.setField(reserva, "estadoPago", estadoPago);
        ReflectionTestUtils.setField(reserva, "transaccionId", transaccionId);
        return reserva;
    }
}
