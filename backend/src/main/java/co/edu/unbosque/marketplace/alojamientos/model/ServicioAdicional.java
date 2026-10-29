package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Servicio con costo que ofrece una publicación (RF-22), como el desayuno o un
 * traslado. A diferencia del catálogo de servicios, es propio de cada una.
 */
@Entity
@Table(name = "servicio_adicional")
public class ServicioAdicional {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "publicacion_id", nullable = false, updatable = false)
	private Publicacion publicacion;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal costo;

	protected ServicioAdicional() {
		// Requerido por JPA.
	}

	// Solo Publicacion crea y actualiza sus servicios adicionales, igual que sus imágenes.
	ServicioAdicional(Publicacion publicacion, String nombre, BigDecimal costo) {
		this.publicacion = publicacion;
		actualizar(nombre, costo);
	}

	void actualizar(String nombre, BigDecimal costo) {
		this.nombre = nombre;
		this.costo = costo;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public BigDecimal getCosto() {
		return costo;
	}
}
