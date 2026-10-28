package co.edu.unbosque.marketplace.compartido.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
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
}
