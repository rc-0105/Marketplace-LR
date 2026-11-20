package co.edu.unbosque.marketplace.disponibilidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.CalendarioNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.RangoCalendarioInvalidoException;
import co.edu.unbosque.marketplace.disponibilidad.model.Calendario;
import co.edu.unbosque.marketplace.disponibilidad.model.DiaCalendario;
import co.edu.unbosque.marketplace.disponibilidad.model.EstadoDia;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.repository.PeriodoDisponibleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendarioServiceTest {

    private static final Long PUBLICACION_ID = 10L;
    private static final Long DUENO_ID = 1L;
    private static final Long HUESPED_ID = 2L;
    // 2026-11-17 04:30 UTC es el 16 de noviembre a las 23:30 en Bogotá: "hoy" se mide en hora de Colombia.
    private static final Clock RELOJ = reloj("2026-11-17T04:30:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);

    private final PeriodoDisponibleRepository periodos = mock(PeriodoDisponibleRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final ConsultaReservasCalendario reservas = mock(ConsultaReservasCalendario.class);
    private final CalendarioService servicio = new CalendarioService(periodos, publicaciones, reservas, RELOJ);

    @BeforeEach
    void preparar() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID))
                .thenReturn(Optional.of(new AccesoPublicacion(DUENO_ID, true)));
    }

    @Test
    @DisplayName("Sin parámetros la ventana es [hoy, hoy + 12 meses) con el día de Bogotá, no el de UTC")
    void ventanaPorDefectoUsaElDiaDeBogota() {
        Calendario calendario = servicio.consultar(PUBLICACION_ID, HUESPED_ID, null, null);

        assertThat(calendario.desde()).isEqualTo(HOY);
        assertThat(calendario.hasta()).isEqualTo(LocalDate.of(2027, 11, 16));
        assertThat(calendario.dias()).hasSize(365);
        assertThat(calendario.dias().get(0).fecha()).isEqualTo(HOY);
        assertThat(calendario.dias().get(364).fecha()).isEqualTo(LocalDate.of(2027, 11, 15));
    }

    @Test
    @DisplayName("Con el reloj a las 21:00 de Bogotá, que ya es el día siguiente en UTC, hoy sigue siendo el de Bogotá")
    void aLasNuevePmEnBogotaHoyEsElMismoDia() {
        CalendarioService otro = new CalendarioService(periodos, publicaciones, reservas,
                reloj("2026-11-17T02:00:00Z"));

        assertThat(otro.consultar(PUBLICACION_ID, HUESPED_ID, null, null).desde()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("Si la ventana de 12 meses cruza un 29 de febrero tiene 366 días y se acepta")
    void ventanaPorDefectoDeUnAnoBisiestoTiene366Dias() {
        CalendarioService otro = new CalendarioService(periodos, publicaciones, reservas,
                reloj("2027-11-20T15:00:00Z"));

        Calendario calendario = otro.consultar(PUBLICACION_ID, HUESPED_ID, null, null);

        assertThat(calendario.hasta()).isEqualTo(LocalDate.of(2028, 11, 20));
        assertThat(calendario.dias()).hasSize(366);
    }

    @Test
    @DisplayName("Solo con desde, hasta es desde más 12 meses")
    void soloDesdeCompletaConDoceMeses() {
        Calendario calendario = servicio.consultar(PUBLICACION_ID, HUESPED_ID, fecha("2026-12-01"), null);

        assertThat(calendario.desde()).isEqualTo(fecha("2026-12-01"));
        assertThat(calendario.hasta()).isEqualTo(fecha("2027-12-01"));
    }

    @Test
    @DisplayName("Solo con hasta, desde es hoy")
    void soloHastaEmpiezaHoy() {
        Calendario calendario = servicio.consultar(PUBLICACION_ID, HUESPED_ID, null, fecha("2026-11-20"));

        assertThat(calendario.desde()).isEqualTo(HOY);
        assertThat(calendario.dias()).extracting(DiaCalendario::fecha).containsExactly(fecha("2026-11-16"),
                fecha("2026-11-17"), fecha("2026-11-18"), fecha("2026-11-19"));
    }

    @Test
    @DisplayName("Se acepta un desde anterior a hoy: el calendario no oculta el pasado")
    void aceptaFechasPasadas() {
        Calendario calendario = servicio.consultar(PUBLICACION_ID, HUESPED_ID, fecha("2026-11-01"),
                fecha("2026-11-03"));

        assertThat(calendario.dias()).extracting(DiaCalendario::fecha).containsExactly(fecha("2026-11-01"),
                fecha("2026-11-02"));
    }

    @Test
    @DisplayName("Con hasta menor que desde responde 400 sin consultar periodos ni reservas")
    void hastaMenorQueDesdeEs400SinConsultar() {
        LocalDate desde = fecha("2026-12-10");
        LocalDate hasta = fecha("2026-12-09");

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, DUENO_ID, desde, hasta))
                .isInstanceOf(RangoCalendarioInvalidoException.class)
                .hasMessage("La fecha final no puede ser anterior a la inicial.");

        verifyNoInteractions(periodos, reservas);
    }

    @Test
    @DisplayName("Una ventana de 366 días se acepta y una de 367 responde 400 sin consultar")
    void elTopeDe366DiasEsInclusivo() {
        Calendario calendario = servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2026-01-01"),
                fecha("2027-01-02"));
        assertThat(calendario.dias()).hasSize(366);

        LocalDate desde = fecha("2026-01-01");
        LocalDate hasta = fecha("2027-01-03");

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, DUENO_ID, desde, hasta))
                .isInstanceOf(RangoCalendarioInvalidoException.class)
                .hasMessage("El calendario no puede abarcar más de 366 días.");
        verify(reservas, never()).ocupantesEntre(PUBLICACION_ID, fecha("2026-01-01"), fecha("2027-01-03"));
    }

    @Test
    @DisplayName("Con hasta igual a desde responde un calendario vacío y calcula tienePeriodos")
    void ventanaVaciaDevuelveSinDias() {
        when(periodos.existsByPublicacionId(PUBLICACION_ID)).thenReturn(true);

        Calendario calendario = servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2026-12-05"),
                fecha("2026-12-05"));

        assertThat(calendario.dias()).isEmpty();
        assertThat(calendario.tienePeriodos()).isTrue();
    }

    @Test
    @DisplayName("El repositorio y el puerto reciben hasta tal cual, sin sumarle un día")
    void hastaViajaSinMasUno() {
        servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2026-12-01"), fecha("2026-12-10"));

        verify(periodos).findByPublicacionIdAndFechaInicioLessThanAndFechaFinGreaterThan(PUBLICACION_ID,
                fecha("2026-12-10"), fecha("2026-12-01"));
        verify(reservas).ocupantesEntre(PUBLICACION_ID, fecha("2026-12-01"), fecha("2026-12-10"));
    }

    @Test
    @DisplayName("Una publicación que no existe responde 404 sin consultar periodos ni reservas")
    void publicacionInexistenteEs404() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, HUESPED_ID, null, null))
                .isInstanceOf(CalendarioNoEncontradoException.class);

        verifyNoInteractions(periodos, reservas);
    }

    @Test
    @DisplayName("Una publicación no ACTIVA responde 404 a quien no es el dueño, igual que una inexistente")
    void publicacionNoActivaEs404ParaQuienNoEsElDueno() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID))
                .thenReturn(Optional.of(new AccesoPublicacion(DUENO_ID, false)));
        CalendarioNoEncontradoException noActiva = capturar(HUESPED_ID);
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.empty());
        CalendarioNoEncontradoException inexistente = capturar(HUESPED_ID);

        assertThat(noActiva.getEstado()).isEqualTo(inexistente.getEstado());
        assertThat(noActiva.getMessage()).isEqualTo(inexistente.getMessage()).isEqualTo("No encontramos esa publicación.");
        verifyNoInteractions(periodos, reservas);
    }

    @Test
    @DisplayName("El dueño ve su publicación aunque no esté ACTIVA y recibe el id de cada reserva")
    void duenoVeSuPublicacionNoActivaConReservaId() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID))
                .thenReturn(Optional.of(new AccesoPublicacion(DUENO_ID, false)));
        when(reservas.ocupantesEntre(PUBLICACION_ID, fecha("2026-12-01"), fecha("2026-12-10")))
                .thenReturn(List.of(new ReservaOcupada(77L, fecha("2026-12-03"), fecha("2026-12-06"))));

        Calendario calendario = servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2026-12-01"),
                fecha("2026-12-10"));

        assertThat(calendario.dias()).filteredOn(dia -> dia.estado() == EstadoDia.RESERVADO).hasSize(3)
                .extracting(DiaCalendario::reservaId).containsOnly(77L);
    }

    @Test
    @DisplayName("Un huésped o un anfitrión que no es el dueño ve la publicación ACTIVA sin ids de reserva")
    void quienNoEsDuenoNoVeReservaId() {
        when(reservas.ocupantesEntre(PUBLICACION_ID, fecha("2026-12-01"), fecha("2026-12-10")))
                .thenReturn(List.of(new ReservaOcupada(77L, fecha("2026-12-03"), fecha("2026-12-06"))));

        Calendario calendario = servicio.consultar(PUBLICACION_ID, HUESPED_ID, fecha("2026-12-01"),
                fecha("2026-12-10"));

        assertThat(calendario.dias()).filteredOn(dia -> dia.estado() == EstadoDia.RESERVADO).hasSize(3)
                .extracting(DiaCalendario::reservaId).containsOnlyNulls();
    }

    @Test
    @DisplayName("Primero se decide el acceso y después el rango: un 404 gana sobre un 400")
    void elAccesoSeEvaluaAntesQueElRango() {
        when(publicaciones.consultarAcceso(PUBLICACION_ID)).thenReturn(Optional.empty());

        LocalDate desde = fecha("2026-12-10");
        LocalDate hasta = fecha("2026-12-09");

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, HUESPED_ID, desde, hasta))
                .isInstanceOf(CalendarioNoEncontradoException.class);
    }

    @Test
    @DisplayName("Los periodos y las reservas definen el estado de cada día de la ventana")
    void calculaLosDiasConPeriodosYReservas() {
        when(periodos.findByPublicacionIdAndFechaInicioLessThanAndFechaFinGreaterThan(PUBLICACION_ID,
                fecha("2026-12-10"), fecha("2026-12-01")))
                .thenReturn(List.of(PeriodoDisponible.nuevo(PUBLICACION_ID, fecha("2026-12-01"),
                        fecha("2026-12-05"))));
        when(reservas.ocupantesEntre(PUBLICACION_ID, fecha("2026-12-01"), fecha("2026-12-10")))
                .thenReturn(List.of(new ReservaOcupada(77L, fecha("2026-12-04"), fecha("2026-12-07"))));

        Calendario calendario = servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2026-12-01"),
                fecha("2026-12-10"));

        assertThat(calendario.publicacionId()).isEqualTo(PUBLICACION_ID);
        assertThat(calendario.dias()).extracting(DiaCalendario::estado).containsExactly(EstadoDia.DISPONIBLE,
                EstadoDia.DISPONIBLE, EstadoDia.DISPONIBLE, EstadoDia.RESERVADO, EstadoDia.RESERVADO,
                EstadoDia.RESERVADO, EstadoDia.BLOQUEADO, EstadoDia.BLOQUEADO, EstadoDia.BLOQUEADO);
    }

    @Test
    @DisplayName("tienePeriodos es true si la publicación tiene periodos aunque ninguno toque la ventana")
    void tienePeriodosNoDependeDeLaVentana() {
        when(periodos.existsByPublicacionId(PUBLICACION_ID)).thenReturn(true);

        Calendario calendario = servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2027-03-01"),
                fecha("2027-03-05"));

        assertThat(calendario.tienePeriodos()).isTrue();
        assertThat(calendario.dias()).extracting(DiaCalendario::estado).containsOnly(EstadoDia.BLOQUEADO).hasSize(4);
    }

    @Test
    @DisplayName("Sin periodos tienePeriodos es false y solo una reserva libera un día del bloqueo")
    void sinPeriodosTodoBloqueadoSalvoReservas() {
        when(periodos.existsByPublicacionId(PUBLICACION_ID)).thenReturn(false);
        when(reservas.ocupantesEntre(PUBLICACION_ID, fecha("2026-12-01"), fecha("2026-12-04")))
                .thenReturn(List.of(new ReservaOcupada(80L, fecha("2026-12-02"), fecha("2026-12-03"))));

        Calendario calendario = servicio.consultar(PUBLICACION_ID, DUENO_ID, fecha("2026-12-01"),
                fecha("2026-12-04"));

        assertThat(calendario.tienePeriodos()).isFalse();
        assertThat(calendario.dias()).extracting(DiaCalendario::estado).containsExactly(EstadoDia.BLOQUEADO,
                EstadoDia.RESERVADO, EstadoDia.BLOQUEADO);
    }

    private CalendarioNoEncontradoException capturar(Long usuarioId) {
        try {
            servicio.consultar(PUBLICACION_ID, usuarioId, null, null);
        } catch (CalendarioNoEncontradoException e) {
            return e;
        }
        throw new AssertionError("Debía lanzar CalendarioNoEncontradoException");
    }

    private static Clock reloj(String instante) {
        return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
    }

    private static LocalDate fecha(String iso) {
        return LocalDate.parse(iso);
    }
}
