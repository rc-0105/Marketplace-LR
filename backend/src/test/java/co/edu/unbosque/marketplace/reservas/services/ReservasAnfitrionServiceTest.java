package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.identidad.services.DirectorioUsuariosService;
import co.edu.unbosque.marketplace.identidad.services.HuespedBasico;
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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Orquestación del listado del anfitrión (SOF-36), con el reloj fijo. */
class ReservasAnfitrionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-12-02T03:00:00Z");
    private static final Long ANFITRION_ID = 7L;

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final DirectorioUsuariosService usuarios = mock(DirectorioUsuariosService.class);
    private final ReservasAnfitrionService servicio = new ReservasAnfitrionService(reservas, publicaciones, usuarios,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Con publicacionId, exige la propiedad antes de consultar y limita la búsqueda a esa publicación")
    void conPublicacionIdExigeLaPropiedad() {
        Reserva reserva = reserva(1L, 10L, 50L, EstadoReserva.CONFIRMADA, LocalDate.of(2026, 12, 10));
        when(reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(10L))).thenReturn(List.of(reserva));
        when(publicaciones.consultarReservados(Set.of(10L))).thenReturn(Map.of(10L, alojamiento(10L)));
        when(usuarios.consultarBasicos(Set.of(50L))).thenReturn(Map.of(50L, new HuespedBasico(50L, "Ana Gómez")));

        List<ReservaDeAnfitrion> resultado = servicio.delAnfitrion(ANFITRION_ID, 10L);

        verify(publicaciones).exigirPropietario(10L, ANFITRION_ID);
        verify(publicaciones, never()).listarDelAnfitrion(any());
        assertThat(resultado).extracting(r -> r.reserva().getId()).containsExactly(1L);
    }

    @Test
    @DisplayName("Una publicación inexistente responde 404 antes de verificar el dueño (orden del design)")
    void publicacionInexistenteResponde404() {
        doThrow(new PublicacionNoEncontradaException()).when(publicaciones).exigirPropietario(99L, ANFITRION_ID);

        assertThatThrownBy(() -> servicio.delAnfitrion(ANFITRION_ID, 99L))
                .isInstanceOf(PublicacionNoEncontradaException.class);
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Una publicación de otro anfitrión responde 403")
    void publicacionDeOtroAnfitrionResponde403() {
        doThrow(new AccesoDenegadoException()).when(publicaciones).exigirPropietario(10L, ANFITRION_ID);

        assertThatThrownBy(() -> servicio.delAnfitrion(ANFITRION_ID, 10L))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("Sin publicacionId, resuelve los ids a partir de las publicaciones del anfitrión")
    void sinPublicacionIdResuelveLosIdsDelAnfitrion() {
        Publicacion p1 = publicacionCon(1L, ANFITRION_ID);
        Publicacion p2 = publicacionCon(2L, ANFITRION_ID);
        when(publicaciones.listarDelAnfitrion(ANFITRION_ID)).thenReturn(List.of(p1, p2));
        when(reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(1L, 2L))).thenReturn(List.of());
        when(publicaciones.consultarReservados(Set.of())).thenReturn(Map.of());
        when(usuarios.consultarBasicos(Set.of())).thenReturn(Map.of());

        servicio.delAnfitrion(ANFITRION_ID, null);

        verify(publicaciones, never()).exigirPropietario(any(), any());
        verify(reservas).findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(1L, 2L));
    }

    @Test
    @DisplayName("Sin publicaciones, devuelve una lista vacía sin consultar el repositorio (D7)")
    void sinPublicacionesDevuelveListaVacia() {
        when(publicaciones.listarDelAnfitrion(ANFITRION_ID)).thenReturn(List.of());

        List<ReservaDeAnfitrion> resultado = servicio.delAnfitrion(ANFITRION_ID, null);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(reservas);
        verify(publicaciones, never()).consultarReservados(any());
        verify(usuarios, never()).consultarBasicos(any());
    }

    @Test
    @DisplayName("El estado expuesto es el actual según el reloj, no el estado crudo guardado")
    void elEstadoExpuestoEsElActual() {
        // PENDIENTE con el apartado ya agotado: estadoActual la ve VENCIDA.
        Reserva agotada = reservaConVencimiento(1L, 10L, 50L, EstadoReserva.PENDIENTE,
                LocalDate.of(2026, 12, 10), AHORA.minusSeconds(60));
        when(reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(10L))).thenReturn(List.of(agotada));
        when(publicaciones.consultarReservados(Set.of(10L))).thenReturn(Map.of(10L, alojamiento(10L)));
        when(usuarios.consultarBasicos(Set.of(50L))).thenReturn(Map.of(50L, new HuespedBasico(50L, "Ana Gómez")));

        List<ReservaDeAnfitrion> resultado = servicio.delAnfitrion(ANFITRION_ID, 10L);

        assertThat(resultado).singleElement().extracting(ReservaDeAnfitrion::estadoActual)
                .isEqualTo(EstadoReserva.VENCIDA);
    }

    @Test
    @DisplayName("Si la publicación o la cuenta del huésped ya no existen, la fila aparece igual, sin enriquecer")
    void sinAlojamientoNiHuespedLaFilaAparece() {
        Reserva huerfana = reserva(1L, 10L, 50L, EstadoReserva.CONFIRMADA, LocalDate.of(2026, 12, 10));
        when(reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(10L))).thenReturn(List.of(huerfana));
        when(publicaciones.consultarReservados(Set.of(10L))).thenReturn(Map.of());
        when(usuarios.consultarBasicos(Set.of(50L))).thenReturn(Map.of());

        List<ReservaDeAnfitrion> resultado = servicio.delAnfitrion(ANFITRION_ID, 10L);

        assertThat(resultado).singleElement().satisfies(fila -> {
            assertThat(fila.alojamiento()).isNull();
            assertThat(fila.huesped()).isNull();
        });
    }

    private static AlojamientoReservado alojamiento(Long id) {
        return new AlojamientoReservado(id, "Casa en la playa", "Santa Marta", null, Map.of(), null);
    }

    private static Publicacion publicacionCon(Long id, Long anfitrionId) {
        Publicacion publicacion = Publicacion.borrador(anfitrionId, new DatosBorrador("Casa", null, null, null, null,
                null, null, Set.of(), null, List.of()));
        ReflectionTestUtils.setField(publicacion, "id", id);
        return publicacion;
    }

    private static Reserva reserva(Long id, Long publicacionId, Long huespedId, EstadoReserva estado,
            LocalDate llegada) {
        return reservaConVencimiento(id, publicacionId, huespedId, estado, llegada, AHORA.plusSeconds(900));
    }

    private static Reserva reservaConVencimiento(Long id, Long publicacionId, Long huespedId, EstadoReserva estado,
            LocalDate llegada, Instant venceEn) {
        Reserva reserva = Reserva.nueva(publicacionId, huespedId, llegada, llegada.plusDays(2), 2, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("220000"), Set.of(),
                venceEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }
}
