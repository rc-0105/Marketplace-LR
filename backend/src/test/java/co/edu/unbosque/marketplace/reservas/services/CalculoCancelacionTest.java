package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculoCancelacionTest {
    @ParameterizedTest
    @CsvSource({"Flexible,2,50,192,false,100,101.01", "Flexible,2,50,48,false,100,101.01",
            "Flexible,2,50,36,false,50,50.51", "Flexible,1,50,36,false,100,101.01",
            "Moderada,5,50,120,false,100,101.01", "Moderada,5,50,119,false,50,50.51",
            "Estricta,14,0,336,false,100,101.01", "Estricta,14,0,335,false,0,0.00",
            "Estricta,14,0,1,true,100,101.01", ",,,1,false,100,101.01"})
    void aplicaTerminosReservados(String nombre, Integer dias, Integer tardio, long horas, boolean anfitrion,
            int esperado, BigDecimal importe) {
        var llegada = LocalDate.of(2027, 1, 10);
        var ahora = Instant.parse("2027-01-10T05:00:00Z").minusSeconds(horas * 3600);
        var resultado = CalculoCancelacion.calcular(nombre, dias, tardio, llegada, ahora,
                new BigDecimal("101.01"), anfitrion);
        assertThat(resultado.porcentaje()).isEqualTo(esperado);
        assertThat(resultado.valorDevolucion()).isEqualTo(importe);
    }
}
