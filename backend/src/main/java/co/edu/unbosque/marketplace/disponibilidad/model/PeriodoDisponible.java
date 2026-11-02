package co.edu.unbosque.marketplace.disponibilidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Rango de fechas en que el anfitrión ofrece su alojamiento (RF-23). Es semiabierto:
 * la fecha final es el día de salida y no cuenta como noche disponible.
 */
@Entity
@Table(name = "periodo_disponible")
public class PeriodoDisponible {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Solo el id: la publicación pertenece a otro módulo y no se navega desde aquí (ADR-2).
	@Column(name = "publicacion_id", nullable = false, updatable = false)
	private Long publicacionId;

	@Column(name = "fecha_inicio", nullable = false)
	private LocalDate fechaInicio;

	@Column(name = "fecha_fin", nullable = false)
	private LocalDate fechaFin;

	protected PeriodoDisponible() {
		// Requerido por JPA.
	}

	private PeriodoDisponible(Long publicacionId, LocalDate fechaInicio, LocalDate fechaFin) {
		this.publicacionId = publicacionId;
		this.fechaInicio = fechaInicio;
		this.fechaFin = fechaFin;
	}

	/** Las fechas llegan validadas por el servicio, que conoce el día de hoy. */
	public static PeriodoDisponible nuevo(Long publicacionId, LocalDate fechaInicio, LocalDate fechaFin) {
		return new PeriodoDisponible(publicacionId, fechaInicio, fechaFin);
	}

	public void cambiarFechas(LocalDate fechaInicio, LocalDate fechaFin) {
		this.fechaInicio = fechaInicio;
		this.fechaFin = fechaFin;
	}

	/** Dos rangos semiabiertos se cruzan si comparten al menos una noche. */
	public boolean seCruzaCon(LocalDate inicio, LocalDate fin) {
		return fechaInicio.isBefore(fin) && inicio.isBefore(fechaFin);
	}

	public Long getId() {
		return id;
	}

	public Long getPublicacionId() {
		return publicacionId;
	}

	public LocalDate getFechaInicio() {
		return fechaInicio;
	}

	public LocalDate getFechaFin() {
		return fechaFin;
	}
}
