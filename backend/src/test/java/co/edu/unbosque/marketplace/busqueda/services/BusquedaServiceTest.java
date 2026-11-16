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

        assertThat(servicio.buscar("Santa Marta", LLEGADA, SALIDA)).containsExactly(libre);
    }

    @Test
    @DisplayName("Si ninguna publicación cumple devuelve una lista vacía, no un error")
    void sinResultadosDevuelveListaVacia() {
        when(publicaciones.buscarCandidatos("Leticia")).thenReturn(List.of());
        when(disponibilidad.filtrarDisponibles(Set.of(), LLEGADA, SALIDA)).thenReturn(Set.of());

        assertThat(servicio.buscar("Leticia", LLEGADA, SALIDA)).isEmpty();
    }

    @Test
    @DisplayName("Si la salida no es posterior a la llegada no ejecuta la búsqueda")
    void rechazaSalidaNoPosteriorALaLlegada() {
        assertThatThrownBy(() -> servicio.buscar("Santa Marta", LLEGADA, LLEGADA))
                .isInstanceOf(BusquedaInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscar("Santa Marta", SALIDA, LLEGADA))
                .isInstanceOf(BusquedaInvalidaException.class);

        verifyNoInteractions(publicaciones, disponibilidad);
    }

    private static CandidatoBusqueda candidato(Long id) {
        return new CandidatoBusqueda(id, "Casa " + id, "Santa Marta", "Casa", 4, new BigDecimal("250000"), null);
    }
}
