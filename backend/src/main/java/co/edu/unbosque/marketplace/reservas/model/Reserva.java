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

/**
 * Reserva sobre una cotización vigente (SOF-33, RF-35, RF-36). El desglose del precio queda
 * fijo en el momento de confirmar: no se recalcula si luego cambian las tarifas de la
 * publicación. Nace PENDIENTE; el pago, que es aparte (ADR-4), es lo que la confirma.
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

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    protected Reserva() {
        // Requerido por JPA.
    }

    private Reserva(Long publicacionId, Long huespedId, LocalDate llegada, LocalDate salida, int noches,
            BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento,
            BigDecimal totalServiciosAdicionales, BigDecimal total) {
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
    }

    /** El desglose llega ya calculado por la cotización vigente que originó la reserva. */
    public static Reserva nueva(Long publicacionId, Long huespedId, LocalDate llegada, LocalDate salida, int noches,
            BigDecimal precioBase, BigDecimal tarifaLimpieza, BigDecimal tarifaServicio, BigDecimal descuento,
            BigDecimal totalServiciosAdicionales, BigDecimal total) {
        return new Reserva(publicacionId, huespedId, llegada, salida, noches, precioBase, tarifaLimpieza,
                tarifaServicio, descuento, totalServiciosAdicionales, total);
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

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
