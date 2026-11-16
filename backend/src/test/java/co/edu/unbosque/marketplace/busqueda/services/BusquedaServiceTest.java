package co.edu.unbosque.marketplace.busqueda.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.CandidatoBusqueda;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.busqueda.exceptions.BusquedaInvalidaException;
import co.edu.unbosque.marketplace.disponibilidad.services.DisponibilidadService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusquedaServiceTest {

    private static final LocalDate LLEGADA = LocalDate.of(2026, 12, 20);
    private static final LocalDate SALIDA = LocalDate.of(2026, 12, 25);

    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final DisponibilidadService disponibilidad = mock(DisponibilidadService.class);
    private final BusquedaService servicio = new BusquedaService(publicaciones, disponibilidad);

    @Test
    @DisplayName("Devuelve solo los candidatos de la ciudad que tienen disponibilidad en el rango")
    void devuelveSoloLosQueTienenDisponibilidad() {
        CandidatoBusqueda libre = candidato(1L);
        CandidatoBusqueda ocupada = candidato(2L);
        when(publicaciones.buscarCandidatos("Santa Marta")).thenReturn(List.of(libre, ocupada));
        when(disponibilidad.filtrarDisponibles(Set.of(1L, 2L), LLEGADA, SALIDA)).thenReturn(Set.of(1L));

        assertThat(servicio.buscar("Santa Marta", LLEGADA, SALIDA, FiltrosBusqueda.ninguno())).containsExactly(libre);
    }

    @Test
    @DisplayName("Si ninguna publicación cumple devuelve una lista vacía, no un error")
    void sinResultadosDevuelveListaVacia() {
        when(publicaciones.buscarCandidatos("Leticia")).thenReturn(List.of());
        when(disponibilidad.filtrarDisponibles(Set.of(), LLEGADA, SALIDA)).thenReturn(Set.of());

        assertThat(servicio.buscar("Leticia", LLEGADA, SALIDA, FiltrosBusqueda.ninguno())).isEmpty();
    }

    @Test
    @DisplayName("Si la salida no es posterior a la llegada no ejecuta la búsqueda")
    void rechazaSalidaNoPosteriorALaLlegada() {
        assertThatThrownBy(() -> servicio.buscar("Santa Marta", LLEGADA, LLEGADA, FiltrosBusqueda.ninguno()))
                .isInstanceOf(BusquedaInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscar("Santa Marta", SALIDA, LLEGADA, FiltrosBusqueda.ninguno()))
                .isInstanceOf(BusquedaInvalidaException.class);

        verifyNoInteractions(publicaciones, disponibilidad);
    }

    @Test
    @DisplayName("Con varios filtros devuelve solo lo que cumple los cuatro a la vez")
    void combinaTodosLosFiltros() {
        CandidatoBusqueda cumple = candidato(1L, "Casa", 6, "300000", Set.of(10L, 20L, 30L));
        CandidatoBusqueda pocaCapacidad = candidato(2L, "Casa", 2, "300000", Set.of(10L, 20L));
        CandidatoBusqueda otroTipo = candidato(3L, "Apartamento", 6, "300000", Set.of(10L, 20L));
        CandidatoBusqueda muyCara = candidato(4L, "Casa", 6, "900000", Set.of(10L, 20L));
        CandidatoBusqueda sinServicio = candidato(5L, "Casa", 6, "300000", Set.of(10L));
        todosDisponibles(cumple, pocaCapacidad, otroTipo, muyCara, sinServicio);

        FiltrosBusqueda filtros = new FiltrosBusqueda(4, "casa", new BigDecimal("100000"),
                new BigDecimal("500000"), Set.of(10L, 20L));

        assertThat(servicio.buscar("Santa Marta", LLEGADA, SALIDA, filtros)).containsExactly(cumple);
    }

    @Test
    @DisplayName("Al quitar un filtro se recalcula sin perder los demás")
    void alQuitarUnFiltroConservaLosDemas() {
        CandidatoBusqueda casa = candidato(1L, "Casa", 6, "300000", Set.of(10L));
        CandidatoBusqueda apartamento = candidato(2L, "Apartamento", 6, "300000", Set.of(10L));
        CandidatoBusqueda casaPequena = candidato(3L, "Casa", 2, "300000", Set.of(10L));
        todosDisponibles(casa, apartamento, casaPequena);

        FiltrosBusqueda conTipo = new FiltrosBusqueda(4, "Casa", null, null, null);
        FiltrosBusqueda sinTipo = new FiltrosBusqueda(4, null, null, null, null);

        assertThat(servicio.buscar("Santa Marta", LLEGADA, SALIDA, conTipo)).containsExactly(casa);
        assertThat(servicio.buscar("Santa Marta", LLEGADA, SALIDA, sinTipo)).containsExactly(casa, apartamento);
    }

    @Test
    @DisplayName("Los límites del rango de precio son inclusivos")
    void rangoDePrecioInclusivo() {
        CandidatoBusqueda enElMinimo = candidato(1L, "Casa", 4, "100000", Set.of());
        CandidatoBusqueda enElMaximo = candidato(2L, "Casa", 4, "500000", Set.of());
        CandidatoBusqueda fuera = candidato(3L, "Casa", 4, "500001", Set.of());
        todosDisponibles(enElMinimo, enElMaximo, fuera);

        FiltrosBusqueda filtros = new FiltrosBusqueda(null, null, new BigDecimal("100000"),
                new BigDecimal("500000"), null);

        assertThat(servicio.buscar("Santa Marta", LLEGADA, SALIDA, filtros))
                .containsExactly(enElMinimo, enElMaximo);
    }

    @Test
    @DisplayName("Si el precio mínimo supera al máximo no ejecuta la búsqueda")
    void rechazaRangoDePrecioInvertido() {
        FiltrosBusqueda filtros = new FiltrosBusqueda(null, null, new BigDecimal("500000"),
                new BigDecimal("100000"), null);

        assertThatThrownBy(() -> servicio.buscar("Santa Marta", LLEGADA, SALIDA, filtros))
                .isInstanceOf(BusquedaInvalidaException.class);

        verifyNoInteractions(publicaciones, disponibilidad);
    }

    private void todosDisponibles(CandidatoBusqueda... candidatos) {
        List<CandidatoBusqueda> lista = List.of(candidatos);
        Set<Long> ids = new HashSet<>();
        for (CandidatoBusqueda candidato : lista) {
            ids.add(candidato.id());
        }
        when(publicaciones.buscarCandidatos("Santa Marta")).thenReturn(lista);
        when(disponibilidad.filtrarDisponibles(ids, LLEGADA, SALIDA)).thenReturn(ids);
    }

    private static CandidatoBusqueda candidato(Long id, String tipo, int capacidad, String precio,
            Set<Long> servicios) {
        return new CandidatoBusqueda(id, "Casa " + id, "Santa Marta", tipo, capacidad, new BigDecimal(precio), null,
                servicios);
    }

    private static CandidatoBusqueda candidato(Long id) {
        return candidato(id, "Casa", 4, "250000", Set.of());
    }
}
