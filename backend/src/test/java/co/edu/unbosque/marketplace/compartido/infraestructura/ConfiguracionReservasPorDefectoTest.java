package co.edu.unbosque.marketplace.compartido.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasPeriodo;
import co.edu.unbosque.marketplace.compartido.consultas.RevisionReservasPublicacion;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import java.time.LocalDate;
import java.util.List;
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

    @Test
    @DisplayName("Sin otra implementación, el calendario no tiene reservas que ocupen días")
    void sinOtroBeanElCalendarioNoTieneReservas() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(ConsultaReservasCalendario.class);
            ConsultaReservasCalendario puerto = ctx.getBean(ConsultaReservasCalendario.class);
            assertThat(puerto.ocupantesEntre(30L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1))).isEmpty();
            assertThat(puerto.ocupantesEntre(31L, LocalDate.of(2028, 5, 1), LocalDate.of(2028, 5, 9))).isEmpty();
        });
    }

    @Test
    @DisplayName("Una implementación real del calendario reemplaza al bean por defecto y sus reservas ocupan días")
    void laConsultaRealDelCalendarioReemplazaAlPorDefecto() {
        ReservaOcupada reserva = new ReservaOcupada(77L, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 6));

        contexto.withBean(ConsultaReservasCalendario.class, () -> (publicacion, desde, hasta) -> List.of(reserva))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ConsultaReservasCalendario.class);
                    assertThat(ctx.getBean(ConsultaReservasCalendario.class).ocupantesEntre(10L,
                            LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10))).containsExactly(reserva);
                });
    }

    @Test
    @DisplayName("Sin otra implementación, la consulta por lote del bean por defecto no marca ninguna publicación como ocupada")
    void sinOtroBeanElLoteVieneVacio() {
        contexto.run(ctx -> {
            ConsultaReservasCalendario puerto = ctx.getBean(ConsultaReservasCalendario.class);
            assertThat(puerto.publicacionesOcupadas(List.of(10L, 11L, 12L),
                    LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 6))).isEmpty();
        });
    }

    @Test
    @DisplayName("SOF-83: sin otra implementación, bloquear una publicación no mueve ninguna reserva a revisión")
    void sinOtroBeanNingunaReservaPasaARevision() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(RevisionReservasPublicacion.class);
            assertThat(ctx.getBean(RevisionReservasPublicacion.class).enviarARevision(30L)).isZero();
        });
    }

    @Test
    @DisplayName("SOF-83: una implementación real de la revisión reemplaza al bean por defecto")
    void laRevisionRealReemplazaAlPorDefecto() {
        contexto.withBean(RevisionReservasPublicacion.class, () -> publicacion -> 2).run(ctx -> {
            assertThat(ctx).hasSingleBean(RevisionReservasPublicacion.class);
            assertThat(ctx.getBean(RevisionReservasPublicacion.class).enviarARevision(30L)).isEqualTo(2);
        });
    }
}
