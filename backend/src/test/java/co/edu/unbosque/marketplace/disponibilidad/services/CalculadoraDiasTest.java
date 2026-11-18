package co.edu.unbosque.marketplace.disponibilidad.services;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.disponibilidad.model.DiaCalendario;
import co.edu.unbosque.marketplace.disponibilidad.model.EstadoDia;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalculadoraDiasTest {

    private static final Long PUBLICACION_ID = 10L;
    private static final List<PeriodoDisponible> PERIODOS_BASE = List.of(periodo("2026-12-01", "2026-12-10"),
            periodo("2026-12-15", "2026-12-20"));

    @Test
    @DisplayName("Con hasta igual a desde no hay días aunque existan periodos y reservas")
    void ventanaVaciaNoTieneDias() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-05"), fecha("2026-12-05"), PERIODOS_BASE,
                List.of(reserva(77L, "2026-12-03", "2026-12-06")), true);

        assertThat(dias).isEmpty();
    }

    @Test
    @DisplayName("Hay un día por cada fecha de [desde, hasta), en orden, y hasta no aparece")
    void unDiaPorFechaDeLaVentanaSemiabierta() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-11-30"), fecha("2026-12-04"), PERIODOS_BASE,
                List.of(), true);

        assertThat(dias).extracting(DiaCalendario::fecha).containsExactly(fecha("2026-11-30"), fecha("2026-12-01"),
                fecha("2026-12-02"), fecha("2026-12-03"));
    }

    @Test
    @DisplayName("Un día es DISPONIBLE dentro de un periodo y BLOQUEADO fuera de todos")
    void disponibleDentroDelPeriodoYBloqueadoFuera() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-11-30"), fecha("2026-12-12"), PERIODOS_BASE,
                List.of(), true);

        assertThat(dias).hasSize(12);
        assertThat(estadoDe(dias, "2026-11-30")).isEqualTo(EstadoDia.BLOQUEADO);
        for (int dia = 1; dia <= 9; dia++) {
            assertThat(estadoDe(dias, "2026-12-0" + dia)).isEqualTo(EstadoDia.DISPONIBLE);
        }
        assertThat(estadoDe(dias, "2026-12-10")).isEqualTo(EstadoDia.BLOQUEADO);
        assertThat(estadoDe(dias, "2026-12-11")).isEqualTo(EstadoDia.BLOQUEADO);
    }

    @Test
    @DisplayName("El día fin de un periodo no cuenta como cubierto, aunque otro periodo empiece después")
    void elDiaFinDelPeriodoQuedaBloqueado() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-09"), fecha("2026-12-16"), PERIODOS_BASE,
                List.of(), true);

        assertThat(estadoDe(dias, "2026-12-09")).isEqualTo(EstadoDia.DISPONIBLE);
        assertThat(estadoDe(dias, "2026-12-10")).isEqualTo(EstadoDia.BLOQUEADO);
        assertThat(estadoDe(dias, "2026-12-14")).isEqualTo(EstadoDia.BLOQUEADO);
        assertThat(estadoDe(dias, "2026-12-15")).isEqualTo(EstadoDia.DISPONIBLE);
    }

    @Test
    @DisplayName("Un periodo que se sale de la ventana se recorta en los dos bordes")
    void recortaElPeriodoEnLosBordesDeLaVentana() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-03"), fecha("2026-12-06"),
                List.of(periodo("2026-11-01", "2027-01-01")), List.of(), true);

        assertThat(dias).extracting(DiaCalendario::estado).containsExactly(EstadoDia.DISPONIBLE,
                EstadoDia.DISPONIBLE, EstadoDia.DISPONIBLE);
    }

    @Test
    @DisplayName("Un periodo totalmente fuera de la ventana no cambia ningún día")
    void ignoraElPeriodoFueraDeLaVentana() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-20"), fecha("2026-12-23"), PERIODOS_BASE,
                List.of(), true);

        assertThat(dias).extracting(DiaCalendario::estado).containsOnly(EstadoDia.BLOQUEADO).hasSize(3);
    }

    @Test
    @DisplayName("Una reserva gana sobre el periodo y el dueño ve su id solo en los días ocupados")
    void reservadoGanaSobreDisponible() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-01"), fecha("2026-12-10"), PERIODOS_BASE,
                List.of(reserva(77L, "2026-12-03", "2026-12-06")), true);

        assertThat(dias).hasSize(9);
        assertThat(dias.get(1)).isEqualTo(new DiaCalendario(fecha("2026-12-02"), EstadoDia.DISPONIBLE, null));
        assertThat(dias.get(2)).isEqualTo(new DiaCalendario(fecha("2026-12-03"), EstadoDia.RESERVADO, 77L));
        assertThat(dias.get(3)).isEqualTo(new DiaCalendario(fecha("2026-12-04"), EstadoDia.RESERVADO, 77L));
        assertThat(dias.get(4)).isEqualTo(new DiaCalendario(fecha("2026-12-05"), EstadoDia.RESERVADO, 77L));
        assertThat(dias.get(5)).isEqualTo(new DiaCalendario(fecha("2026-12-06"), EstadoDia.DISPONIBLE, null));
    }

    @Test
    @DisplayName("Una reserva fuera de todo periodo también ocupa sus días")
    void reservadoGanaSobreBloqueado() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-10"), fecha("2026-12-14"), PERIODOS_BASE,
                List.of(reserva(78L, "2026-12-11", "2026-12-13")), true);

        assertThat(dias).extracting(DiaCalendario::estado).containsExactly(EstadoDia.BLOQUEADO,
                EstadoDia.RESERVADO, EstadoDia.RESERVADO, EstadoDia.BLOQUEADO);
        assertThat(dias).extracting(DiaCalendario::reservaId).containsExactly(null, 78L, 78L, null);
    }

    @Test
    @DisplayName("Una reserva que empieza antes y termina después de la ventana se recorta a sus días")
    void recortaLaReservaEnLosBordesDeLaVentana() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-09"), fecha("2026-12-11"), PERIODOS_BASE,
                List.of(reserva(79L, "2026-12-08", "2026-12-12")), true);

        assertThat(dias).containsExactly(new DiaCalendario(fecha("2026-12-09"), EstadoDia.RESERVADO, 79L),
                new DiaCalendario(fecha("2026-12-10"), EstadoDia.RESERVADO, 79L));
    }

    @Test
    @DisplayName("Una reserva que termina justo al empezar la ventana no ocupa ningún día")
    void laReservaQueTerminaAlEmpezarLaVentanaNoOcupa() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-06"), fecha("2026-12-08"), PERIODOS_BASE,
                List.of(reserva(77L, "2026-12-03", "2026-12-06")), true);

        assertThat(dias).extracting(DiaCalendario::estado).containsExactly(EstadoDia.DISPONIBLE,
                EstadoDia.DISPONIBLE);
    }

    @Test
    @DisplayName("Sin permiso de dueño los días reservados salen sin id de reserva")
    void sinPermisoDeDuenoNoHayReservaId() {
        List<DiaCalendario> dias = CalculadoraDias.calcular(fecha("2026-12-01"), fecha("2026-12-10"), PERIODOS_BASE,
                List.of(reserva(77L, "2026-12-03", "2026-12-06")), false);

        assertThat(dias).filteredOn(dia -> dia.estado() == EstadoDia.RESERVADO).hasSize(3)
                .extracting(DiaCalendario::reservaId).containsOnlyNulls();
    }

    private static EstadoDia estadoDe(List<DiaCalendario> dias, String fecha) {
        return dias.stream().filter(dia -> dia.fecha().equals(fecha(fecha))).findFirst().orElseThrow().estado();
    }

    private static LocalDate fecha(String iso) {
        return LocalDate.parse(iso);
    }

    private static PeriodoDisponible periodo(String inicio, String fin) {
        return PeriodoDisponible.nuevo(PUBLICACION_ID, fecha(inicio), fecha(fin));
    }

    private static ReservaOcupada reserva(Long id, String llegada, String salida) {
        return new ReservaOcupada(id, fecha(llegada), fecha(salida));
    }
}
