package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.cotizacion.exceptions.CotizacionInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.services.Cotizacion;
import co.edu.unbosque.marketplace.cotizacion.services.CotizacionService;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.EstanciaNoDisponibleException;
import co.edu.unbosque.marketplace.disponibilidad.model.MotivoNoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import co.edu.unbosque.marketplace.reservas.exceptions.FechasReservaInvalidasException;
import co.edu.unbosque.marketplace.reservas.exceptions.ModoReservaNoSoportadoException;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaSuperpuestaException;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Creación de una reserva sobre una cotización vigente (SOF-33). */
class ReservaServiceTest {

    private static final Long PUBLICACION_ID = 30L;
    private static final Long HUESPED_ID = 7L;
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 10);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 14);

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final CotizacionService cotizaciones = mock(CotizacionService.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final DisponibilidadService disponibilidad = mock(DisponibilidadService.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    // Hoy en Bogotá es 2026-12-01: la llegada del 10 es futura.
    private final Clock reloj = Clock.fixed(Instant.parse("2026-12-01T15:00:00Z"), ZoneOffset.UTC);
    private final ReservaService servicio = new ReservaService(reservas, cotizaciones, publicaciones, disponibilidad,
            publicador, reloj);

    @BeforeEach
    void preparar() {
        when(reservas.findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(any(), any(), any(), any(),
                eq(EstadoReserva.CANCELADA))).thenReturn(Optional.empty());
        when(reservas.saveAndFlush(any(Reserva.class))).then(AdditionalAnswers.returnsFirstArg());
        when(cotizaciones.cotizar(eq(PUBLICACION_ID), eq(LLEGADA), eq(SALIDA), anySet())).thenReturn(cotizacion());
        when(publicaciones.esModoInmediato(PUBLICACION_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("Con modo INMEDIATA, crea la reserva PENDIENTE con el desglose de la cotización y la audita")
    void creaLaReservaPendienteConElDesgloseYAudita() {
        Reserva reserva = servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of());

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(reserva.getPublicacionId()).isEqualTo(PUBLICACION_ID);
        assertThat(reserva.getHuespedId()).isEqualTo(HUESPED_ID);
        assertThat(reserva.getTotal()).isEqualByComparingTo("485000");
        verify(reservas).saveAndFlush(reserva);
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue().usuario()).isEqualTo("7");
        assertThat(evento.getValue().accion()).isEqualTo(AccionCritica.RESERVA);
        assertThat(evento.getValue().resultado()).isEqualTo(ResultadoEvento.EXITO);
    }

    @Test
    @DisplayName("AC5: si la cotización ya no es vigente porque la publicación no está activa, responde 404 y no guarda")
    void publicacionPausadaDespuesDeCotizarSePropaga() {
        when(cotizaciones.cotizar(eq(PUBLICACION_ID), eq(LLEGADA), eq(SALIDA), anySet()))
                .thenThrow(new PublicacionNoEncontradaException());

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of()))
                .isInstanceOf(PublicacionNoEncontradaException.class);
        verify(reservas, never()).saveAndFlush(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("AC4: si la cotización ya no es vigente, la excepción de cotización se propaga y no guarda nada")
    void cotizacionInvalidaSePropagaSinGuardar() {
        when(cotizaciones.cotizar(eq(PUBLICACION_ID), eq(LLEGADA), eq(SALIDA), anySet()))
                .thenThrow(new CotizacionInvalidaException("Un servicio adicional no pertenece a esta publicación"));

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of()))
                .isInstanceOf(CotizacionInvalidaException.class);
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Si la publicación exige aprobación, rechaza la reserva sin cotizar ni guardar nada")
    void modoAprobacionRechazaLaReserva() {
        when(publicaciones.esModoInmediato(PUBLICACION_ID)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of()))
                .isInstanceOf(ModoReservaNoSoportadoException.class);
        verify(cotizaciones, never()).cotizar(any(), any(), any(), any());
        verify(reservas, never()).saveAndFlush(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Un reintento con los mismos datos devuelve la reserva ya existente sin recotizar ni duplicar")
    void reintentoIdempotenteDevuelveLaReservaExistente() {
        Reserva existente = reservaExistente();
        when(reservas.findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(HUESPED_ID, PUBLICACION_ID,
                LLEGADA, SALIDA, EstadoReserva.CANCELADA)).thenReturn(Optional.of(existente));

        Reserva resultado = servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of());

        assertThat(resultado).isSameAs(existente);
        verify(cotizaciones, never()).cotizar(any(), any(), any(), any());
        verify(publicaciones, never()).esModoInmediato(any());
        verify(reservas, never()).saveAndFlush(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("AC2 y AC3: si PostgreSQL rechaza por cruzarse con otra reserva, responde que ya no está disponible")
    void violacionDeExclusionResponde409SinAuditar() {
        when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(violacion("reserva_sin_cruce", "23P01"));

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of()))
                .isInstanceOf(ReservaSuperpuestaException.class);
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Otra violación de integridad no se disfraza de solapamiento")
    void otraViolacionSePropagaSinCambios() {
        DataIntegrityViolationException otra = violacion("reserva_fechas_check", "23514");
        when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(otra);

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of()))
                .isSameAs(otra);
    }

    @Test
    @DisplayName("Una estancia fuera de los periodos disponibles se rechaza con 409 antes de guardar")
    void estanciaFueraDePeriodoSeRechazaSinGuardar() {
        doThrow(new EstanciaNoDisponibleException(MotivoNoDisponible.FUERA_DE_PERIODO)).when(disponibilidad)
                .exigirDisponible(PUBLICACION_ID, LLEGADA, SALIDA);

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of()))
                .isInstanceOf(EstanciaNoDisponibleException.class);
        verify(reservas, never()).saveAndFlush(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Se valida la disponibilidad después de cotizar (rango válido) y antes de guardar")
    void validaLaDisponibilidadDespuesDeCotizarYAntesDeGuardar() {
        servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of());

        var orden = inOrder(publicaciones, cotizaciones, disponibilidad, reservas);
        orden.verify(publicaciones).esModoInmediato(PUBLICACION_ID);
        orden.verify(cotizaciones).cotizar(eq(PUBLICACION_ID), eq(LLEGADA), eq(SALIDA), anySet());
        orden.verify(disponibilidad).exigirDisponible(PUBLICACION_ID, LLEGADA, SALIDA);
        orden.verify(reservas).saveAndFlush(any(Reserva.class));
    }

    @Test
    @DisplayName("Un reintento idempotente no vuelve a exigir disponibilidad: la reserva propia ya ocupa las noches")
    void elReintentoNoExigeDisponibilidad() {
        when(reservas.findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(HUESPED_ID, PUBLICACION_ID,
                LLEGADA, SALIDA, EstadoReserva.CANCELADA)).thenReturn(Optional.of(reservaExistente()));

        servicio.crear(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, Set.of());

        verify(disponibilidad, never()).exigirDisponible(any(), any(), any());
    }

    @Test
    @DisplayName("No se reserva con llegada anterior a hoy (día de Bogotá): responde 400 sin tocar nada")
    void rechazaUnaLlegadaPasada() {
        LocalDate ayer = LocalDate.of(2026, 11, 30);

        assertThatThrownBy(() -> servicio.crear(PUBLICACION_ID, HUESPED_ID, ayer, SALIDA, Set.of()))
                .isInstanceOf(FechasReservaInvalidasException.class)
                .hasMessage("La fecha de llegada no puede ser anterior a hoy.");
        verify(cotizaciones, never()).cotizar(any(), any(), any(), any());
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Llegar hoy es válido, y 'hoy' se mide en Bogotá y no en UTC")
    void llegarHoyEsValidoSegunLaZonaDeBogota() {
        // 2026-12-02T03:00Z es todavía 2026-12-01 en Bogotá (UTC-5).
        Clock madrugadaUtc = Clock.fixed(Instant.parse("2026-12-02T03:00:00Z"), ZoneOffset.UTC);
        ReservaService enMadrugada = new ReservaService(reservas, cotizaciones, publicaciones, disponibilidad, publicador,
                madrugadaUtc);
        LocalDate hoy = LocalDate.of(2026, 12, 1);
        when(cotizaciones.cotizar(eq(PUBLICACION_ID), eq(hoy), eq(SALIDA), anySet())).thenReturn(cotizacion());

        assertThat(enMadrugada.crear(PUBLICACION_ID, HUESPED_ID, hoy, SALIDA, Set.of()).getLlegada()).isEqualTo(hoy);
    }

    private static Cotizacion cotizacion() {
        return new Cotizacion(PUBLICACION_ID, 4, new BigDecimal("100000"), new BigDecimal("400000"),
                new BigDecimal("30000"), new BigDecimal("40000"), BigDecimal.ZERO, List.of(),
                new BigDecimal("15000"), new BigDecimal("485000"));
    }

    private static Reserva reservaExistente() {
        Reserva reserva = Reserva.nueva(PUBLICACION_ID, HUESPED_ID, LLEGADA, SALIDA, 4, new BigDecimal("400000"),
                new BigDecimal("30000"), new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("15000"),
                new BigDecimal("485000"));
        ReflectionTestUtils.setField(reserva, "id", 99L);
        return reserva;
    }

    // Así llega a Spring la violación de una restricción de PostgreSQL al hacer flush.
    private static DataIntegrityViolationException violacion(String restriccion, String estadoSql) {
        SQLException sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"%s\"".formatted(restriccion),
                estadoSql);
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, null));
    }
}
