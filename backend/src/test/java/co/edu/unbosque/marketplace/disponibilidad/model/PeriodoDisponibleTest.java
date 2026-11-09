package co.edu.unbosque.marketplace.disponibilidad.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodoDisponibleTest {

	private static final LocalDate DIA_10 = LocalDate.of(2027, 1, 10);
	private static final LocalDate DIA_15 = LocalDate.of(2027, 1, 15);
	private final PeriodoDisponible periodo = PeriodoDisponible.nuevo(30L, DIA_10, DIA_15);

	@Test
	@DisplayName("Un periodo nuevo guarda la publicación y las fechas recibidas")
	void nuevoGuardaPublicacionYFechas() {
		assertThat(periodo.getPublicacionId()).isEqualTo(30L);
		assertThat(periodo.getFechaInicio()).isEqualTo(DIA_10);
		assertThat(periodo.getFechaFin()).isEqualTo(DIA_15);
	}

	@Test
	@DisplayName("Se cruza con un rango que comparte al menos una noche")
	void seCruzaConRangoQueComparteNoches() {
		assertThat(periodo.seCruzaCon(DIA_10.minusDays(2), DIA_10.plusDays(1))).isTrue();
		assertThat(periodo.seCruzaCon(DIA_15.minusDays(1), DIA_15.plusDays(3))).isTrue();
		assertThat(periodo.seCruzaCon(DIA_10.plusDays(1), DIA_15.minusDays(1))).isTrue();
	}

	@Test
	@DisplayName("No se cruza con un rango contiguo: el día final de uno puede ser el inicial del otro")
	void noSeCruzaConRangoContiguo() {
		assertThat(periodo.seCruzaCon(DIA_15, DIA_15.plusDays(4))).isFalse();
		assertThat(periodo.seCruzaCon(DIA_10.minusDays(4), DIA_10)).isFalse();
	}

	@Test
	@DisplayName("Cambiar las fechas reemplaza el rango del periodo")
	void cambiarFechasReemplazaElRango() {
		periodo.cambiarFechas(DIA_15, DIA_15.plusDays(7));

		assertThat(periodo.getFechaInicio()).isEqualTo(DIA_15);
		assertThat(periodo.getFechaFin()).isEqualTo(DIA_15.plusDays(7));
		assertThat(periodo.getPublicacionId()).isEqualTo(30L);
	}
}
