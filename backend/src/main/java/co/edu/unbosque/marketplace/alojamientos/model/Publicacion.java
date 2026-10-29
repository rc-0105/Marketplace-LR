package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

	// N:M con el catálogo, como en el modelo relacional (AlojamientoServicio).
	@ManyToMany
	@JoinTable(name = "publicacion_servicio", joinColumns = @JoinColumn(name = "publicacion_id"),
			inverseJoinColumns = @JoinColumn(name = "servicio_id"))
	@OrderBy("nombre")
	private Set<Servicio> servicios = new LinkedHashSet<>();

	// Set y no List: findById trae servicios e imágenes en la misma consulta, y una
	// List sin columna de orden (bag) repetiría cada imagen una vez por servicio.
	@OneToMany(mappedBy = "publicacion", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("posicion")
	private Set<ImagenPublicacion> imagenes = new LinkedHashSet<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPublicacion estado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "editado_por")
	private Long editadoPor;

	@Column(name = "editado_en")
	private Instant editadoEn;

	protected Publicacion() {
		// Requerido por JPA.
	}

	private Publicacion(Long anfitrionId, DatosBorrador datos) {
		this.anfitrionId = anfitrionId;
		aplicar(datos);
		this.estado = EstadoPublicacion.BORRADOR;
		this.creadoEn = Instant.now();
	}

	/** Toda publicación nueva nace en BORRADOR, sin importar qué datos traiga. */
	public static Publicacion borrador(Long anfitrionId, DatosBorrador datos) {
		return new Publicacion(anfitrionId, datos);
	}

	/**
	 * Reemplaza todos los datos editables (RF-13) y registra quién editó y cuándo.
	 * El dueño, el estado y la fecha de creación no cambian.
	 */
	public void editar(DatosBorrador datos, Long editorId) {
		aplicar(datos);
		this.editadoPor = editorId;
		this.editadoEn = Instant.now();
	}

	private void aplicar(DatosBorrador datos) {
		this.titulo = datos.titulo();
		this.descripcion = datos.descripcion();
		this.ciudad = datos.ciudad();
		this.direccion = datos.direccion();
		this.tipo = datos.tipo();
		this.capacidad = datos.capacidad();
		this.precioNoche = datos.precioNoche();
		this.reglas = datos.reglas();
		// Sin clear(): Hibernate lo toma como vaciar la colección y reescribe todas
		// las filas de la tabla intermedia. Así solo cambian las que de verdad cambian.
		this.servicios.retainAll(datos.servicios());
		this.servicios.addAll(datos.servicios());
		reemplazarImagenes(datos.imagenes());
	}

	/**
	 * Compara por URL con las imágenes guardadas: la que sigue en la lista conserva
	 * su fila y solo cambia de posición, la nueva se inserta y la que ya no viene la
	 * borra orphanRemoval. Así editar sin tocar las imágenes no reescribe ninguna fila.
	 */
	private void reemplazarImagenes(List<String> urls) {
		Map<String, ImagenPublicacion> actuales = new HashMap<>();
		for (ImagenPublicacion imagen : imagenes) {
			actuales.put(imagen.getUrl(), imagen);
		}
		List<ImagenPublicacion> resultado = new ArrayList<>();
		for (int posicion = 0; posicion < urls.size(); posicion++) {
			String url = urls.get(posicion);
			// remove: si la misma URL llega dos veces, la segunda es una imagen nueva.
			ImagenPublicacion imagen = actuales.remove(url);
			if (imagen == null) {
				imagen = new ImagenPublicacion(this, url, posicion);
			} else {
				imagen.moverA(posicion);
			}
			resultado.add(imagen);
		}
		imagenes.clear();
		imagenes.addAll(resultado);
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

	/** Servicios en orden alfabético, igual que los aplica @OrderBy al leer. */
	public List<Servicio> getServicios() {
		return servicios.stream().sorted(Comparator.comparing(Servicio::getNombre)).toList();
	}

	/** URL de cada imagen en orden; la primera es la principal. */
	public List<String> getImagenes() {
		return imagenes.stream().sorted(Comparator.comparingInt(ImagenPublicacion::getPosicion))
				.map(ImagenPublicacion::getUrl).toList();
	}

	public EstadoPublicacion getEstado() {
		return estado;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Long getEditadoPor() {
		return editadoPor;
	}

	public Instant getEditadoEn() {
		return editadoEn;
	}
}
