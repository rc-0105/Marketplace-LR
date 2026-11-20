package co.edu.unbosque.marketplace.reservas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Reserva sobre una cotización vigente (SOF-33, RF-35, RF-36). El desglose del precio queda
 * fijo en el momento de confirmar: no se recalcula si luego cambian las tarifas de la
 * publicación. Nace PENDIENTE y aparta las noches hasta {@code venceEn}; el pago, que es aparte (ADR-4),
 * es lo que la confirma, y si el apartado se agota pasa a VENCIDA.
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo los identificadores: la publicación y el huésped pertenecen a otros módulos y no se
    // navegan desde aquí (ADR-2).
    @Column(name = "publicacion_id", nullable = false, updatable = false)
    private Long publicacionId;

    @Column(name = "huesped_id", nullable = false, updatable = false)
    private Long huespedId;

    @Column(nullable = false, updatable = false)
    private LocalDate llegada;

    @Column(nullable = false, updatable = false)
    private LocalDate salida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(nullable = false, updatable = false)
    private int noches;

    @Column(name = "precio_base", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal precioBase;

    @Column(name = "tarifa_limpieza", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal tarifaLimpieza;

    @Column(name = "tarifa_servicio", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal tarifaServicio;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal descuento;

    @Column(name = "total_servicios_adicionales", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalServiciosAdicionales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // Ids de los servicios adicionales elegidos, para distinguir un reintento idéntico de otro con extras
    // distintos. Sin llave foránea: son los que se cotizaron, aunque el anfitrión borre el servicio después.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "servicios_adicionales_ids", nullable = false, updatable = false,
            columnDefinition = "bigint[]")
    private Set<Long> serviciosAdicionalesIds;

    // Hasta cuándo una PENDIENTE aparta las noches (ADR-4); la hora la fija el reloj de la aplicación.
    @Column(name = "vence_en", nullable = false, updatable = false)
    private Instant venceEn;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    protected Reserva() {
        // Requerido por JPA.
    }

    private Reserva(Long publicacionId, Long huespedId, LocalDate llegada, LocalDate salida, int noches,
            BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento,
            BigDecimal totalServiciosAdicionales, BigDecimal total, Set<Long> serviciosAdicionalesIds,
            Instant venceEn) {
        this.publicacionId = publicacionId;
        this.huespedId = huespedId;
        this.llegada = llegada;
        this.salida = salida;
        this.estado = EstadoReserva.PENDIENTE;
        this.noches = noches;
        this.precioBase = precioBase;
        this.tarifaLimpieza = tarifaLimpieza;
        this.tarifaServicio = tarifaServicio;
        this.descuento = descuento;
        this.totalServiciosAdicionales = totalServiciosAdicionales;
        this.total = total;
        this.serviciosAdicionalesIds = Set.copyOf(serviciosAdicionalesIds);
        this.venceEn = venceEn;
    }

    /**
     * El desglose llega ya calculado por la cotización vigente que originó la reserva, los servicios adicionales que eligió el huésped y {@code venceEn} es el
     * instante hasta el que la PENDIENTE aparta las noches.
     */
    public static Reserva nueva(Long publicacionId, Long huespedId, LocalDate llegada, LocalDate salida, int noches,
            BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento,
            BigDecimal totalServiciosAdicionales, BigDecimal total, Set<Long> serviciosAdicionalesIds,
            Instant venceEn) {
        return new Reserva(publicacionId, huespedId, llegada, salida, noches, precioBase, tarifaLimpieza,
                tarifaServicio, descuento, totalServiciosAdicionales, total, serviciosAdicionalesIds, venceEn);
    }

    /**
     * Si ocupa noches en {@code ahora}: una CONFIRMADA siempre, una PENDIENTE solo mientras su apartado no
     * haya vencido (el instante de vencimiento ya no cuenta). Es la regla que las consultas replican.
     */
    public boolean ocupaNoches(Instant ahora) {
        return estado == EstadoReserva.CONFIRMADA
                || (estado == EstadoReserva.PENDIENTE && venceEn.isAfter(ahora));
    }

    /** El apartado se agotó sin confirmarse: pasa a VENCIDA y libera las noches. */
    public void vencer() {
        this.estado = EstadoReserva.VENCIDA;
    }

    public Long getId() {
        return id;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public Long getHuespedId() {
        return huespedId;
    }

    public LocalDate getLlegada() {
        return llegada;
    }

    public LocalDate getSalida() {
        return salida;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public int getNoches() {
        return noches;
    }

    public BigDecimal getPrecioBase() {
        return precioBase;
    }

    public BigDecimal getTarifaLimpieza() {
        return tarifaLimpieza;
    }

    public BigDecimal getTarifaServicio() {
        return tarifaServicio;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public BigDecimal getTotalServiciosAdicionales() {
        return totalServiciosAdicionales;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Set<Long> getServiciosAdicionalesIds() {
        return serviciosAdicionalesIds;
    }

    public Instant getVenceEn() {
        return venceEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
