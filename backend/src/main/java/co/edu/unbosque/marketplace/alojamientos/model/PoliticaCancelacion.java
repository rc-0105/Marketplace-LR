package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Política de cancelación del catálogo (RF-20). Cancelar con al menos
 * {@code diasSinCosto} días de anticipación devuelve el 100 %; después se
 * devuelve {@code porcentajeDevolucion}.
 */
@Entity
@Table(name = "politica_cancelacion")
public class PoliticaCancelacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String nombre;

	@Column(nullable = false, length = 300)
	private String descripcion;

	@Column(name = "dias_sin_costo", nullable = false)
	private int diasSinCosto;

	@Column(name = "porcentaje_devolucion", nullable = false)
	private int porcentajeDevolucion;

	protected PoliticaCancelacion() {
		// Requerido por JPA.
	}

	public PoliticaCancelacion(String nombre, String descripcion, int diasSinCosto, int porcentajeDevolucion) {
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.diasSinCosto = diasSinCosto;
		this.porcentajeDevolucion = porcentajeDevolucion;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public int getDiasSinCosto() {
		return diasSinCosto;
	}

	public int getPorcentajeDevolucion() {
		return porcentajeDevolucion;
	}

	// Igualdad por nombre, que es único, igual que en los otros catálogos.
	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		return otro instanceof PoliticaCancelacion politica && Objects.equals(getNombre(), politica.getNombre());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(getNombre());
	}
}
