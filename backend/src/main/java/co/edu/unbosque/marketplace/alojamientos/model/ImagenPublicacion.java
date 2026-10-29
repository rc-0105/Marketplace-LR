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

/**
 * Imagen de una publicación (RF-19). La posición define el orden; la de
 * posición 0 es la imagen principal.
 */
@Entity
@Table(name = "publicacion_imagen")
public class ImagenPublicacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "publicacion_id", nullable = false, updatable = false)
	private Publicacion publicacion;

	@Column(nullable = false)
	private int posicion;

	@Column(nullable = false, length = 500)
	private String url;

	protected ImagenPublicacion() {
		// Requerido por JPA.
	}

	// Solo Publicacion crea y mueve sus imágenes: así la relación siempre queda en los dos lados.
	ImagenPublicacion(Publicacion publicacion, String url, int posicion) {
		this.publicacion = publicacion;
		this.url = url;
		this.posicion = posicion;
	}

	void moverA(int posicion) {
		this.posicion = posicion;
	}

	public Long getId() {
		return id;
	}

	public int getPosicion() {
		return posicion;
	}

	public String getUrl() {
		return url;
	}
}
