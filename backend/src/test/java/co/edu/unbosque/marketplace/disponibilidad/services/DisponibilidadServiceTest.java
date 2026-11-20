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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.CalendarioNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.EstanciaNoDisponibleException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.FechasPeriodoInvalidasException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoConReservaException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.PeriodoSuperpuestoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.RangoConsultaInvalidoException;
import co.edu.unbosque.marketplace.disponibilidad.model.DisponibilidadRango;
import co.edu.unbosque.marketplace.disponibilidad.model.MotivoNoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.repository.PeriodoDisponibleRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

class DisponibilidadServiceTest {

    private static final Long ANFITRION_ID = 7L;
    private static final Long OTRO_ANFITRION_ID = 8L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Long PERIODO_ID = 50L;
    // 2027-01-01 a las 03:00 UTC sigue siendo 31 de diciembre en Bogotá: "hoy" se mide en hora de Colombia.
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2027-01-01T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 12, 31);
    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 3);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 6);

    private final PeriodoDisponibleRepository periodos = mock(PeriodoDisponibleRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final ConsultaReservasPeriodo reservas = mock(ConsultaReservasPeriodo.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    // Un fake y no un mock: Mockito también stubea el método default del puerto y lo devolvería vacío.
    private final OcupacionFalsa ocupacion = new OcupacionFalsa();
    private final DisponibilidadService servicio = new DisponibilidadService(periodos, publicaciones, reservas,
            ocupacion, publicador, RELOJ);

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
    @DisplayName("El calendario público devuelve solo las fechas de cada periodo, sin pedir ser el dueño")
    void listarLibresDevuelveLasFechas() {
        when(periodos.findByPublicacionIdOrderByFechaInicioAsc(PUBLICACION_ID))
                .thenReturn(List.of(periodo(HOY, HOY.plusDays(3))));

        assertThat(servicio.listarLibres(PUBLICACION_ID)).containsExactly(new PeriodoLibre(HOY, HOY.plusDays(3)));
        verifyNoInteractions(publicaciones);
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

    @Test
    @DisplayName("Filtrar disponibles pregunta al repositorio por el rango completo y devuelve solo esos ids")
    void filtrarDisponiblesDevuelveLosQueCubrenLaEstancia() {
        when(periodos.findByPublicacionIdInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                List.of(1L, 2L), HOY, HOY.plusDays(3)))
                .thenReturn(List.of(PeriodoDisponible.nuevo(2L, HOY, HOY.plusDays(5)),
                        PeriodoDisponible.nuevo(2L, HOY.minusDays(9), HOY.plusDays(9))));

        assertThat(servicio.filtrarDisponibles(List.of(1L, 2L), HOY, HOY.plusDays(3))).containsExactly(2L);
    }

    @Test
    @DisplayName("Filtrar disponibles sin publicaciones no consulta la base de datos")
    void filtrarDisponiblesSinPublicacionesNoConsulta() {
        assertThat(servicio.filtrarDisponibles(List.of(), HOY, HOY.plusDays(3))).isEmpty();

        verify(periodos, never())
                .findByPublicacionIdInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(any(), any(), any());
        assertThat(ocupacion.consultadas()).isEmpty();
    }

    @Test
    @DisplayName("Una estancia dentro de un periodo y sin reservas está disponible")
    void estanciaDentroDelPeriodoSinReservasEstaDisponible() {
        conPeriodo(10L);

        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).containsExactly(10L);
        assertThat(ocupacion.consultadas()).containsExactly(10L);
    }

    @Test
    @DisplayName("Una publicación fuera de periodo no está disponible aunque el puerto no tenga reservas")
    void fueraDePeriodoNoEstaDisponible() {
        conPeriodo(10L);

        assertThat(servicio.filtrarDisponibles(List.of(10L, 12L), LLEGADA, SALIDA)).containsExactly(10L);
    }

    @Test
    @DisplayName("No pregunta al puerto por las publicaciones sin periodo que contenga la estancia")
    void noPreguntaAlPuertoPorLasQueNoTienenPeriodo() {
        conPeriodo(10L);
        ocupacion.ocupar(12L, "2026-12-03", "2026-12-06");

        Set<Long> disponibles = servicio.filtrarDisponibles(List.of(10L, 12L), LLEGADA, SALIDA);

        assertThat(disponibles).containsExactly(10L);
        assertThat(ocupacion.consultadas()).containsExactly(10L);
    }

    @Test
    @DisplayName("Con ninguna publicación en periodo no consulta el puerto")
    void sinPublicacionesEnPeriodoNoConsultaElPuerto() {
        conPeriodo();

        assertThat(servicio.filtrarDisponibles(List.of(11L, 12L), LLEGADA, SALIDA)).isEmpty();
        assertThat(ocupacion.consultadas()).isEmpty();
    }

    @Test
    @DisplayName("Una reserva que cruza la estancia excluye la publicación")
    void reservaQueCruzaExcluyeLaPublicacion() {
        conPeriodo(10L);
        ocupacion.ocupar(10L, "2026-12-05", "2026-12-08");

        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).isEmpty();
    }

    @Test
    @DisplayName("Una reserva contenida en la estancia excluye la publicación")
    void reservaContenidaExcluyeLaPublicacion() {
        conPeriodo(10L);
        ocupacion.ocupar(10L, "2026-12-04", "2026-12-05");

        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).isEmpty();
    }

    @Test
    @DisplayName("Una reserva que termina el día de llegada o empieza el de salida no excluye la publicación")
    void reservasAdyacentesNoExcluyen() {
        conPeriodo(10L);
        ocupacion.ocupar(10L, "2026-12-01", "2026-12-03");
        ocupacion.ocupar(10L, "2026-12-06", "2026-12-09");

        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).containsExactly(10L);
    }

    @Test
    @DisplayName("Una reserva de otra publicación no excluye a la que se consulta")
    void reservaDeOtraPublicacionNoExcluye() {
        conPeriodo(10L, 11L);
        ocupacion.ocupar(11L, "2026-12-02", "2026-12-04");

        assertThat(servicio.filtrarDisponibles(List.of(10L, 11L), LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 4)))
                .containsExactly(10L);
    }

    @Test
    @DisplayName("Si el puerto deja de devolver la reserva, la misma estancia vuelve a estar disponible")
    void liberarLaReservaDevuelveLaDisponibilidad() {
        conPeriodo(10L);
        ocupacion.ocupar(10L, "2026-12-03", "2026-12-06");
        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).isEmpty();

        ocupacion.liberar(10L);

        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).containsExactly(10L);
    }

    @Test
    @DisplayName("Filtrar disponibles no escribe ni publica eventos")
    void filtrarDisponiblesEsSoloLectura() {
        conPeriodo(10L);
        ocupacion.ocupar(10L, "2026-12-05", "2026-12-08");

        assertThat(servicio.filtrarDisponibles(List.of(10L), LLEGADA, SALIDA)).isEmpty();
        verify(periodos, never()).saveAndFlush(any());
        verify(periodos, never()).delete(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Consultar una publicación inexistente o no visible responde el mismo 404 sin tocar repositorio ni puerto")
    void consultarNoRevelaLaPublicacionQueNoSeVe() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.empty());
        when(publicaciones.consultarAcceso(31L)).thenReturn(Optional.of(new AccesoPublicacion(ANFITRION_ID, false)));

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, SALIDA))
                .isInstanceOf(CalendarioNoEncontradoException.class).hasMessage("No encontramos esa publicación.");
        assertThatThrownBy(() -> servicio.consultar(31L, OTRO_ANFITRION_ID, LLEGADA, SALIDA))
                .isInstanceOf(CalendarioNoEncontradoException.class).hasMessage("No encontramos esa publicación.");
        verifyNoInteractions(periodos);
        assertThat(ocupacion.consultadas()).isEmpty();
    }

    @Test
    @DisplayName("El dueño consulta su publicación en cualquier estado")
    void elDuenoConsultaUnaPublicacionNoActiva() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.of(new AccesoPublicacion(ANFITRION_ID, false)));
        conPeriodo(PUBLICACION_ID);

        assertThat(servicio.consultar(PUBLICACION_ID, ANFITRION_ID, LLEGADA, SALIDA).disponible()).isTrue();
    }

    @Test
    @DisplayName("Una estancia que termina el día que empieza o antes da 400 sin tocar repositorio ni puerto")
    void rechazaUnRangoInvalido() {
        accesible();

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, LLEGADA))
                .isInstanceOf(RangoConsultaInvalidoException.class);
        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, SALIDA, LLEGADA))
                .isInstanceOf(RangoConsultaInvalidoException.class);
        verifyNoInteractions(periodos);
        assertThat(ocupacion.consultadas()).isEmpty();
    }

    @Test
    @DisplayName("El 404 va antes que el 400: a quien no ve la publicación no se le revela nada más")
    void elNoEncontradoVaAntesQueElRangoInvalido() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, SALIDA, LLEGADA))
                .isInstanceOf(CalendarioNoEncontradoException.class);
    }

    @Test
    @DisplayName("Una estancia dentro de un periodo y sin reservas se consulta como disponible, sin motivo")
    void consultarDisponible() {
        accesible();
        conPeriodo(PUBLICACION_ID);

        DisponibilidadRango rango = servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, SALIDA);

        assertThat(rango).isEqualTo(new DisponibilidadRango(PUBLICACION_ID, LLEGADA, SALIDA, null));
        assertThat(rango.disponible()).isTrue();
    }

    @Test
    @DisplayName("Fuera de periodo, también sin ningún periodo, gana a una reserva y no consulta el puerto")
    void consultarFueraDePeriodoGanaALaReserva() {
        accesible();
        conPeriodo();
        ocupacion.ocupar(PUBLICACION_ID, "2026-12-03", "2026-12-06");

        DisponibilidadRango rango = servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, SALIDA);

        assertThat(rango.motivo()).isEqualTo(MotivoNoDisponible.FUERA_DE_PERIODO);
        assertThat(rango.disponible()).isFalse();
        assertThat(ocupacion.consultadas()).isEmpty();
    }

    @Test
    @DisplayName("Con una reserva que cruza, el motivo es RESERVADO; adyacente no ocupa y liberarla devuelve la disponibilidad")
    void consultarReservadoYLiberacion() {
        accesible();
        conPeriodo(PUBLICACION_ID);
        ocupacion.ocupar(PUBLICACION_ID, "2026-12-01", "2026-12-03");
        ocupacion.ocupar(PUBLICACION_ID, "2026-12-06", "2026-12-09");
        assertThat(servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, SALIDA).motivo()).isNull();
        ocupacion.ocupar(PUBLICACION_ID, "2026-12-05", "2026-12-08");

        DisponibilidadRango reservado = servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, SALIDA);

        assertThat(reservado.motivo()).isEqualTo(MotivoNoDisponible.RESERVADO);
        assertThat(reservado.disponible()).isFalse();
        ocupacion.liberar(PUBLICACION_ID);
        assertThat(servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LLEGADA, SALIDA).motivo()).isNull();
        verify(periodos, never()).saveAndFlush(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("Consultar no tiene tope de días")
    void consultarNoTieneTopeDeRango() {
        accesible();
        conPeriodo(PUBLICACION_ID);

        DisponibilidadRango rango = servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, LocalDate.of(2026, 1, 1),
                LocalDate.of(2028, 1, 1));

        assertThat(rango.hasta()).isEqualTo(LocalDate.of(2028, 1, 1));
    }

    @Test
    @DisplayName("exigirDisponible deja pasar una estancia dentro de un periodo y sin reservas, sin pedir acceso")
    void exigirDisponibleDejaPasarLaEstanciaLibre() {
        conPeriodo(PUBLICACION_ID);

        servicio.exigirDisponible(PUBLICACION_ID, LLEGADA, SALIDA);

        verify(publicaciones, never()).consultarAcceso(any());
    }

    @Test
    @DisplayName("exigirDisponible rechaza con FUERA_DE_PERIODO la estancia que ningún periodo contiene")
    void exigirDisponibleRechazaFueraDePeriodo() {
        conPeriodo();

        assertThatThrownBy(() -> servicio.exigirDisponible(PUBLICACION_ID, LLEGADA, SALIDA))
                .isInstanceOfSatisfying(EstanciaNoDisponibleException.class, error -> {
                    assertThat(error.getMotivo()).isEqualTo(MotivoNoDisponible.FUERA_DE_PERIODO);
                    assertThat(error.getEstado()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(error.getPropiedades()).containsEntry("motivo", "FUERA_DE_PERIODO");
                    assertThat(error.getMessage()).isEqualTo(
                            "Esas fechas no están dentro de un periodo en que el anfitrión ofrece el alojamiento.");
                });
    }

    @Test
    @DisplayName("exigirDisponible rechaza con RESERVADO la estancia que otra reserva ocupa")
    void exigirDisponibleRechazaLoReservado() {
        conPeriodo(PUBLICACION_ID);
        ocupacion.ocupar(PUBLICACION_ID, "2026-12-05", "2026-12-08");

        assertThatThrownBy(() -> servicio.exigirDisponible(PUBLICACION_ID, LLEGADA, SALIDA))
                .isInstanceOfSatisfying(EstanciaNoDisponibleException.class, error -> {
                    assertThat(error.getMotivo()).isEqualTo(MotivoNoDisponible.RESERVADO);
                    assertThat(error.getMessage()).isEqualTo("Esas fechas ya no están disponibles: alguien más reservó primero.");
                });
    }

    private void accesible() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.of(new AccesoPublicacion(ANFITRION_ID, true)));
    }

    // Devuelve un periodo de la publicación solo para los ids pedidos que se declararon con periodo.
    private void conPeriodo(Long... idsConPeriodo) {
        when(periodos.findByPublicacionIdInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(any(), any(), any()))
                .thenAnswer(invocacion -> {
                    Collection<Long> pedidos = invocacion.getArgument(0);
                    return Arrays.stream(idsConPeriodo).filter(pedidos::contains)
                            .map(id -> PeriodoDisponible.nuevo(id, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10)))
                            .toList();
                });
    }

    /** Doble del puerto de reservas con reservas que se agregan y se quitan, y registro de a quién se preguntó. */
    private static final class OcupacionFalsa implements ConsultaReservasCalendario {

        private final Map<Long, List<ReservaOcupada>> reservas = new HashMap<>();
        private final List<Long> consultadas = new ArrayList<>();

        void ocupar(Long publicacionId, String llegada, String salida) {
            reservas.computeIfAbsent(publicacionId, id -> new ArrayList<>())
                    .add(new ReservaOcupada(1L, LocalDate.parse(llegada), LocalDate.parse(salida)));
        }

        void liberar(Long publicacionId) {
            reservas.remove(publicacionId);
        }

        List<Long> consultadas() {
            return consultadas;
        }

        @Override
        public List<ReservaOcupada> ocupantesEntre(Long publicacionId, LocalDate desde, LocalDate hasta) {
            consultadas.add(publicacionId);
            return reservas.getOrDefault(publicacionId, List.of());
        }
    }
}
