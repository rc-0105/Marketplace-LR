package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Tipo de alojamiento del catálogo (casa, apartamento...). La publicación lo
 * elige del catálogo, igual que los servicios (RF-18).
 */
@Entity
@Table(name = "tipo_alojamiento")
public class TipoAlojamiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String nombre;

	protected TipoAlojamiento() {
		// Requerido por JPA.
	}

	public TipoAlojamiento(String nombre) {
		this.nombre = nombre;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	// Igualdad por nombre, que es único, igual que en Servicio.
	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		return otro instanceof TipoAlojamiento tipo && Objects.equals(getNombre(), tipo.getNombre());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(getNombre());
	}
}
