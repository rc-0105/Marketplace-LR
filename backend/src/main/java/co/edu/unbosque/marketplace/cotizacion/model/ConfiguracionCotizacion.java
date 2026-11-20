package co.edu.unbosque.marketplace.cotizacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Porcentajes de la cotización (RF-32). Hay una sola fila, con id 1. */
@Entity
@Table(name = "configuracion_cotizacion")
public class ConfiguracionCotizacion {

    public static final Short ID = 1;

    @Id
    private Short id;

    @Column(name = "porcentaje_servicio", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeServicio;

    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento;

    protected ConfiguracionCotizacion() {
        // Requerido por JPA.
    }

    public ConfiguracionCotizacion(BigDecimal porcentajeServicio, BigDecimal porcentajeDescuento) {
        this.id = ID;
        this.porcentajeServicio = porcentajeServicio;
        this.porcentajeDescuento = porcentajeDescuento;
    }

    public BigDecimal getPorcentajeServicio() {
        return porcentajeServicio;
    }

    public BigDecimal getPorcentajeDescuento() {
        return porcentajeDescuento;
    }
}
