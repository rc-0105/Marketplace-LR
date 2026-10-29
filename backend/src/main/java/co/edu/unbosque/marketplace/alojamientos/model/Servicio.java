package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Servicio del catálogo (wifi, piscina...). Las publicaciones lo eligen del
 * catálogo en vez de escribirlo, para que la búsqueda pueda filtrar por él.
 */
@Entity
@Table(name = "servicio")
public class Servicio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 60)
	private String nombre;

	protected Servicio() {
		// Requerido por JPA.
	}

	public Servicio(String nombre) {
		this.nombre = nombre;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}
}
