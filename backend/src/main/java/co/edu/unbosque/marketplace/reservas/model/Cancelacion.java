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

/**
 * Cancelación registrada de una reserva (SOF-75, HU-30, RF-46/RF-48): quién la canceló, cuándo, el motivo,
 * el valor que se devuelve y el estado final de la reserva. Es el rastro que exige AC2; no vuelve a tocar el
 * estado de la reserva, que ya cambió con la actualización condicional de {@code ReservaRepository}.
 */
@Entity
@Table(name = "cancelacion")
public class Cancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false, updatable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Column(nullable = false, updatable = false, length = 500)
    private String motivo;

    @Column(name = "valor_devolucion", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal valorDevolucion;

    @Column(name = "resultado_reembolso", length = 20)
    private String resultadoReembolso;
    @Column(name = "reembolso_id", length = 80)
    private String reembolsoId;
    @Column(name = "error_reembolso", length = 40)
    private String errorReembolso;

    public String getResultadoReembolso() { return resultadoReembolso; }
    public String getReembolsoId() { return reembolsoId; }

    public void registrarExito(String identificador) {
        reembolsoId = identificador;
        resultadoReembolso = "SIMULADO";
        errorReembolso = null;
    }

    public void registrarFallo() {
        errorReembolso = "FALLO_PASARELA";
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_final", nullable = false, updatable = false, length = 20)
    private EstadoReserva estadoFinal;

    protected Cancelacion() {
        // Requerido por JPA.
    }

    private Cancelacion(Long reservaId, Long usuarioId, Instant fecha, String motivo, BigDecimal valorDevolucion,
            EstadoReserva estadoFinal) {
        this.reservaId = reservaId;
        this.usuarioId = usuarioId;
        this.fecha = fecha;
        this.motivo = motivo;
        this.valorDevolucion = valorDevolucion;
        this.estadoFinal = estadoFinal;
    }

    public void prepararReembolsoSimulado() {
        // La cancelación se confirma antes de contactar con la pasarela.
        resultadoReembolso = valorDevolucion.signum() > 0 ? "REVISION_MANUAL" : "NO_APLICA";
    }

    public static Cancelacion nueva(Long reservaId, Long usuarioId, Instant fecha, String motivo,
            BigDecimal valorDevolucion, EstadoReserva estadoFinal) {
        return new Cancelacion(reservaId, usuarioId, fecha, motivo, valorDevolucion, estadoFinal);
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getMotivo() {
        return motivo;
    }

    public BigDecimal getValorDevolucion() {
        return valorDevolucion;
    }

    public EstadoReserva getEstadoFinal() {
        return estadoFinal;
    }
}
