package co.edu.unbosque.marketplace.compartido.consultas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConsultaReservasCalendarioTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 12, 3);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 6);

    private static ReservaOcupada reserva(String llegada, String salida) {
        return new ReservaOcupada(1L, LocalDate.parse(llegada), LocalDate.parse(salida));
    }

    @ParameterizedTest(name = "[{0}, {1}) sobre la estancia [03, 06) de diciembre: cruza = {2}")
    @CsvSource({
            "2026-12-05, 2026-12-08, true", "2026-12-01, 2026-12-04, true", "2026-12-04, 2026-12-05, true",
            "2026-12-01, 2026-12-10, true", "2026-12-01, 2026-12-03, false", "2026-12-06, 2026-12-09, false",
            "2026-11-01, 2026-11-05, false", "2027-01-10, 2027-01-12, false"})
    @DisplayName("Una reserva cruza la estancia solo si ocupa alguna de sus noches: tocar el borde no cruza")
    void cruzaSoloSiOcupaAlgunaNoche(String llegada, String salida, boolean esperado) {
        assertThat(reserva(llegada, salida).cruza(DESDE, HASTA)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("El lote devuelve solo las publicaciones con una reserva que cruza e itera una vez por id")
    void elLoteDevuelveLasOcupadasEIteraUnaVezPorId() {
        List<Long> consultadas = new ArrayList<>();
        ConsultaReservasCalendario puerto = (publicacionId, desde, hasta) -> {
            consultadas.add(publicacionId);
            return publicacionId.equals(11L) ? List.of(reserva("2026-12-05", "2026-12-08")) : List.of();
        };

        Set<Long> ocupadas = puerto.publicacionesOcupadas(List.of(10L, 11L, 12L), DESDE, HASTA);

        assertThat(ocupadas).containsExactly(11L);
        assertThat(consultadas).containsExactly(10L, 11L, 12L);
    }

    @Test
    @DisplayName("El lote ignora las reservas que solo tocan el borde del rango")
    void elLoteIgnoraLasReservasQueNoCruzan() {
        ConsultaReservasCalendario puerto = (publicacionId, desde, hasta) -> List.of(
                reserva("2026-12-01", "2026-12-03"), reserva("2026-12-06", "2026-12-09"));

        assertThat(puerto.publicacionesOcupadas(List.of(10L), DESDE, HASTA)).isEmpty();
        assertThat(puerto.publicacionesOcupadas(List.of(10L), DESDE.minusDays(1), HASTA)).containsExactly(10L);
    }

    @Test
    @DisplayName("El lote con una colección vacía no invoca el puerto")
    void elLoteVacioNoConsultaElPuerto() {
        AtomicInteger invocaciones = new AtomicInteger();
        ConsultaReservasCalendario puerto = (publicacionId, desde, hasta) -> {
            invocaciones.incrementAndGet();
            return List.of(reserva("2026-12-05", "2026-12-08"));
        };
        Collection<Long> sinIds = List.of();

        assertThat(puerto.publicacionesOcupadas(sinIds, DESDE, HASTA)).isEmpty();
        assertThat(invocaciones).hasValue(0);
        assertThat(puerto.publicacionesOcupadas(List.of(10L), DESDE, HASTA)).containsExactly(10L);
        assertThat(invocaciones).hasValue(1);
    }
}
