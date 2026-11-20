package co.edu.unbosque.marketplace.auditoria.model;

import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila del registro de auditoría (RF-59). Es de solo lectura una vez insertada:
 * Hibernate no la actualiza y el trigger de la tabla rechaza UPDATE y DELETE.
 */
@Entity
@Immutable
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha;

    private String usuario;

    @Column(nullable = false)
    private String accion;

    @Column(nullable = false)
    private String entidad;

    @Column(name = "entidad_id")
    private String entidadId;

    private String correlacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultadoEvento resultado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> datos;

    protected EventoAuditoria() {
        // Requerido por JPA.
    }

    /** Arma la fila de un evento; {@code datos} debe llegar ya saneado. */
    public static EventoAuditoria de(EventoCritico evento, Map<String, Object> datos, String correlacion) {
        EventoAuditoria fila = new EventoAuditoria();
        fila.fecha = evento.fecha();
        fila.usuario = evento.usuario();
        fila.accion = evento.accion().name();
        fila.entidad = evento.entidad();
        fila.entidadId = evento.entidadId();
        fila.correlacion = correlacion;
        fila.resultado = evento.resultado();
        fila.datos = datos;
        return fila;
    }

    public Long getId() {
        return id;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public String getCorrelacion() {
        return correlacion;
    }

    public ResultadoEvento getResultado() {
        return resultado;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }
}
