package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Publicación de un alojamiento (RF-11). Siempre nace en BORRADOR. */
@Entity
@Table(name = "publicacion")
public class Publicacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Solo el identificador: el módulo no toca la entidad Usuario de identidad
	// (ADR-2).
	@Column(name = "anfitrion_id", nullable = false, updatable = false)
	private Long anfitrionId;

	@Column(nullable = false, length = 120)
	private String titulo;

	@Column(length = 2000)
	private String descripcion;

	@Column(length = 100)
	private String ciudad;

	@Column(length = 200)
	private String direccion;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private TipoAlojamiento tipo;

	private Integer capacidad;

	@Column(name = "precio_noche", precision = 12, scale = 2)
	private BigDecimal precioNoche;

	@Column(length = 2000)
	private String reglas;

	@ElementCollection
	@CollectionTable(name = "publicacion_servicio", joinColumns = @JoinColumn(name = "publicacion_id"))
	@Column(name = "servicio", nullable = false, length = 60)
	private Set<String> servicios = new LinkedHashSet<>();

	@ElementCollection
	@CollectionTable(name = "publicacion_imagen", joinColumns = @JoinColumn(name = "publicacion_id"))
	@OrderColumn(name = "posicion")
	@Column(name = "url", nullable = false, length = 500)
	private List<String> imagenes = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPublicacion estado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	protected Publicacion() {
		// Requerido por JPA.
	}

	private Publicacion(Long anfitrionId, DatosBorrador datos) {
		this.anfitrionId = anfitrionId;
		this.titulo = datos.titulo();
		this.descripcion = datos.descripcion();
		this.ciudad = datos.ciudad();
		this.direccion = datos.direccion();
		this.tipo = datos.tipo();
		this.capacidad = datos.capacidad();
		this.precioNoche = datos.precioNoche();
		this.reglas = datos.reglas();
		this.servicios.addAll(datos.servicios());
		this.imagenes.addAll(datos.imagenes());
		this.estado = EstadoPublicacion.BORRADOR;
		this.creadoEn = Instant.now();
	}

	/** Toda publicación nueva nace en BORRADOR, sin importar qué datos traiga. */
	public static Publicacion borrador(Long anfitrionId, DatosBorrador datos) {
		return new Publicacion(anfitrionId, datos);
	}

	/**
	 * Campos que faltan para poder activar la publicación, en el orden del
	 * formulario. Una lista vacía significa que el borrador está completo.
	 */
	public List<String> camposFaltantesParaActivar() {
		List<String> faltantes = new ArrayList<>();
		agregarSiFalta(faltantes, "descripcion", vacio(descripcion));
		agregarSiFalta(faltantes, "ciudad", vacio(ciudad));
		agregarSiFalta(faltantes, "direccion", vacio(direccion));
		agregarSiFalta(faltantes, "tipo", tipo == null);
		agregarSiFalta(faltantes, "capacidad", capacidad == null);
		agregarSiFalta(faltantes, "precioNoche", precioNoche == null);
		agregarSiFalta(faltantes, "servicios", servicios.isEmpty());
		agregarSiFalta(faltantes, "reglas", vacio(reglas));
		agregarSiFalta(faltantes, "imagenes", imagenes.isEmpty());
		return faltantes;
	}

	private static void agregarSiFalta(List<String> faltantes, String campo, boolean falta) {
		if (falta) {
			faltantes.add(campo);
		}
	}

	private static boolean vacio(String valor) {
		return valor == null || valor.isBlank();
	}

	public Long getId() {
		return id;
	}

	public Long getAnfitrionId() {
		return anfitrionId;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getCiudad() {
		return ciudad;
	}

	public String getDireccion() {
		return direccion;
	}

	public TipoAlojamiento getTipo() {
		return tipo;
	}

	public Integer getCapacidad() {
		return capacidad;
	}

	public BigDecimal getPrecioNoche() {
		return precioNoche;
	}

	public String getReglas() {
		return reglas;
	}

	public Set<String> getServicios() {
		return Collections.unmodifiableSet(servicios);
	}

	public List<String> getImagenes() {
		return Collections.unmodifiableList(imagenes);
	}

	public EstadoPublicacion getEstado() {
		return estado;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
