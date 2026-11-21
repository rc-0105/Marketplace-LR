package co.edu.unbosque.marketplace.resenas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.identidad.services.CuentaService;
import co.edu.unbosque.marketplace.resenas.model.EstadoResena;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import co.edu.unbosque.marketplace.resenas.repository.ResenaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Consulta de las reseñas visibles y la calificación promedio de una publicación (SOF-81, HU-36, RF-54). */
class ConsultaResenasServiceTest {

    private static final Long PUBLICACION_ID = 30L;

    private final ResenaRepository resenas = mock(ResenaRepository.class);
    private final CuentaService cuentas = mock(CuentaService.class);
    private final ConsultaResenasService servicio = new ConsultaResenasService(resenas, cuentas);

    private Resena resena(Long id, Long reservaId, Long huespedId, int calificacion, String contenido,
            String fecha) {
        Resena resena = Resena.nueva(reservaId, huespedId, PUBLICACION_ID, Instant.parse(fecha), calificacion,
                contenido);
        ReflectionTestUtils.setField(resena, "id", id);
        return resena;
    }

    private void visibles(Resena... lista) {
        when(resenas.findByPublicacionIdAndEstadoOrderByFechaDesc(PUBLICACION_ID, EstadoResena.VISIBLE))
                .thenReturn(List.of(lista));
    }

    @Test
    @DisplayName("AC1: devuelve el promedio con un decimal y las reseñas en el orden en que las entrega el repositorio")
    void promedioConUnDecimalYResenasVisibles() {
        visibles(
                resena(3L, 13L, 8L, 5, "Excelente", "2026-12-20T10:00:00Z"),
                resena(2L, 12L, 7L, 4, "Muy bien", "2026-12-10T10:00:00Z"),
                resena(1L, 11L, 9L, 4, "Bien", "2026-12-01T10:00:00Z"));
        when(cuentas.nombresDe(Set.of(7L, 8L, 9L))).thenReturn(Map.of(7L, "Ana", 8L, "Luis", 9L, "Marta"));

        ResenasPublicacion respuesta = servicio.consultar(PUBLICACION_ID);

        assertThat(respuesta.promedio()).isEqualTo(4.3);
        assertThat(respuesta.resenas()).extracting(ResenaPublicada::id).containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("AC1: el promedio redondea hacia arriba en la mitad (4.67 se muestra como 4.7)")
    void elPromedioRedondeaALaMitadHaciaArriba() {
        visibles(
                resena(3L, 13L, 8L, 5, "a", "2026-12-20T10:00:00Z"),
                resena(2L, 12L, 7L, 5, "b", "2026-12-10T10:00:00Z"),
                resena(1L, 11L, 9L, 4, "c", "2026-12-01T10:00:00Z"));
        when(cuentas.nombresDe(Set.of(7L, 8L, 9L))).thenReturn(Map.of(7L, "Ana", 8L, "Luis", 9L, "Marta"));

        assertThat(servicio.consultar(PUBLICACION_ID).promedio()).isEqualTo(4.7);
        assertThat(servicio.promedio(PUBLICACION_ID)).isEqualTo(4.7);
    }

    @Test
    @DisplayName("AC2: sin reseñas visibles no hay promedio (nulo, nunca cero) ni lista, y no consulta nombres")
    void sinResenasNoHayPromedio() {
        visibles();

        ResenasPublicacion respuesta = servicio.consultar(PUBLICACION_ID);

        assertThat(respuesta.promedio()).isNull();
        assertThat(respuesta.resenas()).isEmpty();
        assertThat(servicio.promedio(PUBLICACION_ID)).isNull();
        verifyNoInteractions(cuentas);
    }

    @Test
    @DisplayName("AC3: cada reseña trae el autor, la fecha, el contenido y la reserva que le dio origen")
    void cadaResenaTraeAutorFechaContenidoYReserva() {
        visibles(resena(3L, 13L, 8L, 5, "Excelente", "2026-12-20T10:00:00Z"));
        when(cuentas.nombresDe(Set.of(8L))).thenReturn(Map.of(8L, "Luis"));

        ResenaPublicada publicada = servicio.consultar(PUBLICACION_ID).resenas().get(0);

        assertThat(publicada.autor()).isEqualTo("Luis");
        assertThat(publicada.fecha()).isEqualTo(Instant.parse("2026-12-20T10:00:00Z"));
        assertThat(publicada.contenido()).isEqualTo("Excelente");
        assertThat(publicada.reservaId()).isEqualTo(13L);
        assertThat(publicada.calificacion()).isEqualTo(5);
        verify(cuentas).nombresDe(Set.of(8L));
    }
}
