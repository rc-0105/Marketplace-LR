package co.edu.unbosque.marketplace.compartido.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguracionReservasPorDefectoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionReservasPorDefecto.class);

    @Test
    @DisplayName("Sin otra implementación del puerto, el bean por defecto no bloquea a nadie")
    void sinOtroBeanDevuelveCero() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(ConsultaReservasFuturas.class);
            assertThat(ctx.getBean(ConsultaReservasFuturas.class).contarConfirmadasFuturas(7L)).isZero();
            assertThat(ctx.getBean(ConsultaReservasFuturas.class).contarConfirmadasFuturas(99L)).isZero();
        });
    }

    @Test
    @DisplayName("Una implementación real reemplaza al bean por defecto y no quedan dos beans")
    void laImplementacionRealReemplazaAlPorDefecto() {
        contexto.withBean(ConsultaReservasFuturas.class, () -> id -> 3L).run(ctx -> {
            assertThat(ctx).hasSingleBean(ConsultaReservasFuturas.class);
            assertThat(ctx.getBean(ConsultaReservasFuturas.class).contarConfirmadasFuturas(7L)).isEqualTo(3L);
        });
    }

    @Test
    @DisplayName("Sin otra implementación, ninguna reserva bloquea un periodo disponible")
    void sinOtroBeanNingunaReservaBloqueaPeriodos() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(ConsultaReservasPeriodo.class);
            assertThat(ctx.getBean(ConsultaReservasPeriodo.class)
                    .reservaConfirmadaEn(30L, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15))).isEmpty();
        });
    }

    @Test
    @DisplayName("Una implementación real de la consulta de periodos reemplaza al bean por defecto")
    void laConsultaRealDePeriodosReemplazaAlPorDefecto() {
        contexto.withBean(ConsultaReservasPeriodo.class, () -> (publicacion, inicio, fin) -> Optional.of(12L))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ConsultaReservasPeriodo.class);
                    assertThat(ctx.getBean(ConsultaReservasPeriodo.class)
                            .reservaConfirmadaEn(30L, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15)))
                            .contains(12L);
                });
    }
}
