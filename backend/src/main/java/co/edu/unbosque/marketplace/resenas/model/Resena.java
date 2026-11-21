package co.edu.unbosque.marketplace.resenas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Reseña y calificación de un huésped sobre una reserva COMPLETADA (SOF-80, HU-35, RF-53). Queda asociada al
 * huésped, a la reserva, a la publicación y a la fecha. Solo guarda identificadores de los otros módulos
 * (ADR-2) y nunca se elimina: la moderación solo cambia su estado de visibilidad.
 */
@Entity
@Table(name = "resena")
public class Resena {

    public static final int CALIFICACION_MINIMA = 1;
    public static final int CALIFICACION_MAXIMA = 5;
    public static final int CONTENIDO_MAXIMO = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false, updatable = false)
    private Long reservaId;

    @Column(name = "huesped_id", nullable = false, updatable = false)
    private Long huespedId;

    @Column(name = "publicacion_id", nullable = false, updatable = false)
    private Long publicacionId;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Column(nullable = false, updatable = false)
    private int calificacion;

    @Column(nullable = false, updatable = false, length = CONTENIDO_MAXIMO)
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoResena estado;

    protected Resena() {
        // Requerido por JPA.
    }

    private Resena(Long reservaId, Long huespedId, Long publicacionId, Instant fecha, int calificacion,
            String contenido) {
        this.reservaId = reservaId;
        this.huespedId = huespedId;
        this.publicacionId = publicacionId;
        this.fecha = fecha;
        this.calificacion = calificacion;
        this.contenido = contenido;
        this.estado = EstadoResena.VISIBLE;
    }

    public static Resena nueva(Long reservaId, Long huespedId, Long publicacionId, Instant fecha,
            int calificacion, String contenido) {
        if (calificacion < CALIFICACION_MINIMA || calificacion > CALIFICACION_MAXIMA) {
            throw new IllegalArgumentException("La calificación va de 1 a 5");
        }
        return new Resena(reservaId, huespedId, publicacionId, fecha, calificacion, contenido);
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public Long getHuespedId() {
        return huespedId;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public Instant getFecha() {
        return fecha;
    }

    public int getCalificacion() {
        return calificacion;
    }

    public String getContenido() {
        return contenido;
    }

    public EstadoResena getEstado() {
        return estado;
    }
}
