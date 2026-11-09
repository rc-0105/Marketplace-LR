package co.edu.unbosque.marketplace.disponibilidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.FechasPeriodoInvalidasException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoConReservaException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoSuperpuestoException;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.repository.PeriodoDisponibleRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class DisponibilidadServiceTest {

    private static final Long ANFITRION_ID = 7L;
    private static final Long OTRO_ANFITRION_ID = 8L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Long PERIODO_ID = 50L;
    // 2027-01-01 a las 03:00 UTC sigue siendo 31 de diciembre en Bogotá: "hoy" se mide en hora de Colombia.
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2027-01-01T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 12, 31);

    private final PeriodoDisponibleRepository periodos = mock(PeriodoDisponibleRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final ConsultaReservasPeriodo reservas = mock(ConsultaReservasPeriodo.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final DisponibilidadService servicio = new DisponibilidadService(periodos, publicaciones, reservas,
            publicador, RELOJ);

    @BeforeEach
    void preparar() {
        when(periodos.saveAndFlush(any(PeriodoDisponible.class))).then(AdditionalAnswers.returnsFirstArg());
        doThrow(new AccesoDenegadoException()).when(publicaciones).exigirPropietario(anyLong(),
                eq(OTRO_ANFITRION_ID));
    }

    @Test
    @DisplayName("Registrar un periodo con fechas válidas lo guarda en la publicación y lo audita")
    void registrarPeriodoValidoLoGuardaYAudita() {
        PeriodoDisponible periodo = servicio.registrar(PUBLICACION_ID, ANFITRION_ID, HOY, HOY.plusDays(5));

        assertThat(periodo.getPublicacionId()).isEqualTo(PUBLICACION_ID);
        assertThat(periodo.getFechaInicio()).isEqualTo(HOY);
        assertThat(periodo.getFechaFin()).isEqualTo(HOY.plusDays(5));
        verify(periodos).saveAndFlush(periodo);
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue().usuario()).isEqualTo("7");
        assertThat(evento.getValue().accion()).isEqualTo(AccionCritica.PUBLICACION);
        assertThat(evento.getValue().entidad()).isEqualTo("PeriodoDisponible");
        assertThat(evento.getValue().resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.getValue().datos()).containsEntry("operacion", "registrar_periodo")
                .containsEntry("publicacionId", "30");
    }

    @Test
    @DisplayName("Si la fecha final no es posterior a la inicial, rechaza el registro sin guardar nada")
    void rechazaFechaFinalNoPosterior() {
        assertThatThrownBy(() -> servicio.registrar(PUBLICACION_ID, ANFITRION_ID, HOY.plusDays(3), HOY.plusDays(3)))
                .isInstanceOf(FechasPeriodoInvalidasException.class)
                .hasMessage("La fecha final debe ser posterior a la fecha inicial.");
        assertThatThrownBy(() -> servicio.registrar(PUBLICACION_ID, ANFITRION_ID, HOY.plusDays(3), HOY.plusDays(1)))
                .isInstanceOf(FechasPeriodoInvalidasException.class);
        verify(periodos, never()).saveAndFlush(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Rechaza un periodo que empieza antes de hoy en hora de Colombia")
    void rechazaInicioAnteriorAHoy() {
        assertThatThrownBy(() -> servicio.registrar(PUBLICACION_ID, ANFITRION_ID, HOY.minusDays(1), HOY.plusDays(2)))
                .isInstanceOf(FechasPeriodoInvalidasException.class)
                .hasMessage("La fecha inicial no puede ser anterior a hoy.");
        verify(periodos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Si PostgreSQL rechaza el registro por cruzarse con otro periodo, responde que se cruza y no audita")
    void rechazaPeriodoQueSeCruza() {
        when(periodos.saveAndFlush(any(PeriodoDisponible.class)))
                .thenThrow(violacion("periodo_disponible_sin_cruce", "23P01"));

        assertThatThrownBy(() -> servicio.registrar(PUBLICACION_ID, ANFITRION_ID, HOY, HOY.plusDays(4)))
                .isInstanceOf(PeriodoSuperpuestoException.class)
                .hasMessage("El periodo se cruza con otro periodo disponible de la publicación.");
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Si PostgreSQL rechaza la modificación por cruzarse con otro periodo, responde que se cruza")
    void rechazaModificacionQueSeCruza() {
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo(HOY.plusDays(1), HOY.plusDays(4))));
        when(periodos.saveAndFlush(any(PeriodoDisponible.class)))
                .thenThrow(violacion("periodo_disponible_sin_cruce", "23P01"));

        assertThatThrownBy(() -> servicio.modificar(PERIODO_ID, ANFITRION_ID, HOY.plusDays(2), HOY.plusDays(9)))
                .isInstanceOf(PeriodoSuperpuestoException.class);
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Otra violación de integridad no se disfraza de cruce de periodos")
    void otraViolacionSePropagaSinCambios() {
        DataIntegrityViolationException otra = violacion("periodo_disponible_fechas_check", "23514");
        when(periodos.saveAndFlush(any(PeriodoDisponible.class))).thenThrow(otra);

        assertThatThrownBy(() -> servicio.registrar(PUBLICACION_ID, ANFITRION_ID, HOY, HOY.plusDays(4)))
                .isSameAs(otra);
    }

    @Test
    @DisplayName("Un anfitrión no registra periodos en una publicación ajena")
    void noRegistraEnPublicacionAjena() {
        assertThatThrownBy(() -> servicio.registrar(PUBLICACION_ID, OTRO_ANFITRION_ID, HOY, HOY.plusDays(2)))
                .isInstanceOf(AccesoDenegadoException.class);
        verify(periodos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Registrar en una publicación inexistente responde que no existe")
    void registrarEnPublicacionInexistente() {
        doThrow(new PublicacionNoEncontradaException()).when(publicaciones).exigirPropietario(99L, ANFITRION_ID);

        assertThatThrownBy(() -> servicio.registrar(99L, ANFITRION_ID, HOY, HOY.plusDays(2)))
                .isInstanceOf(PublicacionNoEncontradaException.class);
    }

    @Test
    @DisplayName("Listar devuelve los periodos de la publicación del anfitrión en orden de fecha")
    void listarDevuelvePeriodosDelDueno() {
        PeriodoDisponible periodo = periodo(HOY, HOY.plusDays(3));
        when(periodos.findByPublicacionIdOrderByFechaInicioAsc(PUBLICACION_ID)).thenReturn(List.of(periodo));

        assertThat(servicio.listar(PUBLICACION_ID, ANFITRION_ID)).containsExactly(periodo);
        assertThatThrownBy(() -> servicio.listar(PUBLICACION_ID, OTRO_ANFITRION_ID))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("Sin reservas, modificar cambia las fechas del periodo y lo audita")
    void modificarSinReservasCambiaFechas() {
        PeriodoDisponible periodo = periodo(HOY.plusDays(1), HOY.plusDays(4));
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo));

        PeriodoDisponible modificado = servicio.modificar(PERIODO_ID, ANFITRION_ID, HOY.plusDays(2), HOY.plusDays(9));

        assertThat(modificado.getFechaInicio()).isEqualTo(HOY.plusDays(2));
        assertThat(modificado.getFechaFin()).isEqualTo(HOY.plusDays(9));
        verify(periodos).saveAndFlush(periodo);
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue().entidadId()).isEqualTo("50");
        assertThat(evento.getValue().datos()).containsEntry("operacion", "modificar_periodo");
    }

    @Test
    @DisplayName("Un periodo que ya empezó puede cambiar su fecha final sin mover la inicial")
    void periodoEnCursoCambiaSoloLaFechaFinal() {
        PeriodoDisponible periodo = periodo(HOY.minusDays(3), HOY.plusDays(2));
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo));

        servicio.modificar(PERIODO_ID, ANFITRION_ID, HOY.minusDays(3), HOY.plusDays(6));

        assertThat(periodo.getFechaFin()).isEqualTo(HOY.plusDays(6));
        assertThatThrownBy(() -> servicio.modificar(PERIODO_ID, ANFITRION_ID, HOY.minusDays(2), HOY.plusDays(6)))
                .isInstanceOf(FechasPeriodoInvalidasException.class)
                .hasMessage("La fecha inicial no puede ser anterior a hoy.");
    }

    @Test
    @DisplayName("Con una reserva confirmada dentro del periodo, modificar se rechaza e indica la reserva")
    void modificarConReservaSeRechaza() {
        PeriodoDisponible periodo = periodo(HOY.plusDays(1), HOY.plusDays(4));
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo));
        when(reservas.reservaConfirmadaEn(PUBLICACION_ID, HOY.plusDays(1), HOY.plusDays(4)))
                .thenReturn(Optional.of(12L));

        assertThatThrownBy(() -> servicio.modificar(PERIODO_ID, ANFITRION_ID, HOY.plusDays(2), HOY.plusDays(9)))
                .isInstanceOf(PeriodoConReservaException.class)
                .hasMessage("El periodo tiene la reserva 12 confirmada; no se puede modificar ni eliminar.")
                .satisfies(error -> assertThat(((PeriodoConReservaException) error).getPropiedades())
                        .containsEntry("reservaId", 12L));
        assertThat(periodo.getFechaInicio()).isEqualTo(HOY.plusDays(1));
        verify(periodos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Con una reserva confirmada dentro del periodo, eliminar se rechaza e indica la reserva")
    void eliminarConReservaSeRechaza() {
        PeriodoDisponible periodo = periodo(HOY.plusDays(1), HOY.plusDays(4));
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo));
        when(reservas.reservaConfirmadaEn(PUBLICACION_ID, HOY.plusDays(1), HOY.plusDays(4)))
                .thenReturn(Optional.of(12L));

        assertThatThrownBy(() -> servicio.eliminar(PERIODO_ID, ANFITRION_ID))
                .isInstanceOf(PeriodoConReservaException.class)
                .hasMessageContaining("reserva 12");
        verify(periodos, never()).delete(any());
    }

    @Test
    @DisplayName("Sin reservas, eliminar borra el periodo y lo audita")
    void eliminarSinReservasBorra() {
        PeriodoDisponible periodo = periodo(HOY.plusDays(1), HOY.plusDays(4));
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo));

        servicio.eliminar(PERIODO_ID, ANFITRION_ID);

        verify(periodos).delete(periodo);
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue().datos()).containsEntry("operacion", "eliminar_periodo");
    }

    @Test
    @DisplayName("Un anfitrión no modifica ni elimina periodos de una publicación ajena")
    void noModificaNiEliminaPeriodoAjeno() {
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.of(periodo(HOY.plusDays(1), HOY.plusDays(4))));

        assertThatThrownBy(() -> servicio.modificar(PERIODO_ID, OTRO_ANFITRION_ID, HOY.plusDays(2), HOY.plusDays(5)))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.eliminar(PERIODO_ID, OTRO_ANFITRION_ID))
                .isInstanceOf(AccesoDenegadoException.class);
        verify(periodos, never()).saveAndFlush(any());
        verify(periodos, never()).delete(any());
    }

    @Test
    @DisplayName("Modificar o eliminar un periodo inexistente responde que no existe")
    void periodoInexistente() {
        when(periodos.findById(PERIODO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.modificar(PERIODO_ID, ANFITRION_ID, HOY, HOY.plusDays(2)))
                .isInstanceOf(PeriodoNoEncontradoException.class)
                .hasMessage("No encontramos ese periodo.");
        assertThatThrownBy(() -> servicio.eliminar(PERIODO_ID, ANFITRION_ID))
                .isInstanceOf(PeriodoNoEncontradoException.class);
    }

    // Así llega a Spring la violación de una restricción de PostgreSQL al hacer flush:
    // Hibernate no extrae el nombre de una restricción de exclusión, solo queda en el mensaje.
    private static DataIntegrityViolationException violacion(String restriccion, String estadoSql) {
        SQLException sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"%s\"".formatted(restriccion), estadoSql);
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, null));
    }

    private static PeriodoDisponible periodo(LocalDate inicio, LocalDate fin) {
        PeriodoDisponible periodo = PeriodoDisponible.nuevo(PUBLICACION_ID, inicio, fin);
        ReflectionTestUtils.setField(periodo, "id", PERIODO_ID);
        return periodo;
    }
}
