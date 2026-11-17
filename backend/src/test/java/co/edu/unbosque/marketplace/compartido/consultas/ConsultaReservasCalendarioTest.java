package co.edu.unbosque.marketplace.compartido.consultas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultaReservasCalendarioTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 12, 3);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 6);

    private static ReservaOcupada reserva(String llegada, String salida) {
        return new ReservaOcupada(1L, LocalDate.parse(llegada), LocalDate.parse(salida));
    }

    @Test
    @DisplayName("Una reserva que se solapa con la estancia la cruza")
    void cruzaCuandoSeSolapa() {
        assertThat(reserva("2026-12-05", "2026-12-08").cruza(DESDE, HASTA)).isTrue();
        assertThat(reserva("2026-12-01", "2026-12-04").cruza(DESDE, HASTA)).isTrue();
    }

    @Test
    @DisplayName("Una reserva contenida en la estancia la cruza")
    void cruzaCuandoEstaContenida() {
        assertThat(reserva("2026-12-04", "2026-12-05").cruza(DESDE, HASTA)).isTrue();
    }

    @Test
    @DisplayName("Una reserva que sobresale por los dos lados de la estancia la cruza")
    void cruzaCuandoSobresale() {
        assertThat(reserva("2026-12-01", "2026-12-10").cruza(DESDE, HASTA)).isTrue();
    }

    @Test
    @DisplayName("Una reserva que termina el día de llegada no cruza")
    void noCruzaSiTerminaElDiaDeLlegada() {
        assertThat(reserva("2026-12-01", "2026-12-03").cruza(DESDE, HASTA)).isFalse();
    }

    @Test
    @DisplayName("Una reserva que empieza el día de salida no cruza")
    void noCruzaSiEmpiezaElDiaDeSalida() {
        assertThat(reserva("2026-12-06", "2026-12-09").cruza(DESDE, HASTA)).isFalse();
    }

    @Test
    @DisplayName("Una reserva lejana de la estancia no cruza")
    void noCruzaSiEstaLejos() {
        assertThat(reserva("2026-11-01", "2026-11-05").cruza(DESDE, HASTA)).isFalse();
        assertThat(reserva("2027-01-10", "2027-01-12").cruza(DESDE, HASTA)).isFalse();
    }
}
