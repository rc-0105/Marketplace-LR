package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cierre de reservas con la estancia terminada (SOF-78, HU-33, RF-50): una CONFIRMADA cuya fecha de salida
 * ya pasó queda COMPLETADA para habilitar la reseña y mantener los indicadores correctos.
 */
class CierreReservasServiceTest {

    // "Hoy" en Colombia; a las 20:00 UTC todavía es el mismo día en America/Bogota (UTC-5).
    private static final Instant AHORA = Instant.parse("2026-12-01T20:00:00Z");

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final CierreReservasService cierre = new CierreReservasService(reservas, publicador,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("AC1: cierra las reservas con la estancia terminada usando el día de hoy en Colombia")
    void cierraLasReservasConLaFechaDeHoyEnColombia() {
        when(reservas.completarConEstanciaTerminada(LocalDate.of(2026, 12, 1))).thenReturn(3);

        cierre.cerrarReservasConEstanciaTerminada();

        verify(reservas).completarConEstanciaTerminada(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("Si cerró alguna reserva, audita cuántas fueron")
    void siCerroAlgunaAuditaElResultado() {
        when(reservas.completarConEstanciaTerminada(any())).thenReturn(3);

        cierre.cerrarReservasConEstanciaTerminada();

        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue().datos()).containsEntry("cerradas", 3);
    }

    @Test
    @DisplayName("Si no cerró ninguna reserva, no audita nada")
    void siNoCerroNingunaNoAuditaNada() {
        when(reservas.completarConEstanciaTerminada(any())).thenReturn(0);

        cierre.cerrarReservasConEstanciaTerminada();

        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Corre cada marketplace.reservas.cierre.intervalo-horas (24 por defecto)")
    void seProgramaSegunLaConfiguracion() throws NoSuchMethodException {
        Scheduled programacion = CierreReservasService.class
                .getMethod("cerrarReservasConEstanciaTerminada").getAnnotation(Scheduled.class);

        assertThat(programacion.timeUnit()).isEqualTo(TimeUnit.HOURS);
        assertThat(programacion.fixedDelayString()).isEqualTo("${marketplace.reservas.cierre.intervalo-horas:24}");
        assertThat(programacion.initialDelayString()).isEqualTo("${marketplace.reservas.cierre.intervalo-horas:24}");
    }
}
