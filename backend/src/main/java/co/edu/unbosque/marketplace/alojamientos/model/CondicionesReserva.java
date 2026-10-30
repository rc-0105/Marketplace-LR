package co.edu.unbosque.marketplace.alojamientos.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Condiciones comerciales ya validadas que se aplican a una publicación
 * (RF-20, RF-21, RF-22). La política es nula si no se eligió ninguna.
 */
public record CondicionesReserva(
		BigDecimal tarifaLimpieza,
		ModoReserva modoReserva,
		PoliticaCancelacion politica,
		List<Adicional> serviciosAdicionales) {

	/** Servicio adicional con su costo, antes de asociarse a la publicación. */
	public record Adicional(String nombre, BigDecimal costo) {
	}
}
