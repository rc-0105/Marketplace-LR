package co.edu.unbosque.marketplace.notificaciones.model;

import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Un aviso dentro de la plataforma para un usuario (SOF-37, RF-40). Solo cambia al leerse. */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destinatario_id", nullable = false)
    private Long destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacion tipo;

    @Column(nullable = false)
    private String mensaje;

    @Column(nullable = false)
    private String entidad;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(name = "leida_en")
    private Instant leidaEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Notificacion() {
        // Requerido por JPA.
    }

    public static Notificacion nueva(NuevaNotificacion datos, Instant creadoEn) {
        Notificacion notificacion = new Notificacion();
        notificacion.destinatarioId = datos.destinatarioId();
        notificacion.tipo = datos.tipo();
        notificacion.mensaje = datos.mensaje();
        notificacion.entidad = datos.entidad();
        notificacion.entidadId = datos.entidadId();
        notificacion.creadoEn = creadoEn;
        return notificacion;
    }

    /** Marca la lectura; si ya estaba leída conserva la primera fecha, así repetirlo no cambia nada. */
    public void marcarLeida(Instant ahora) {
        if (leidaEn == null) {
            leidaEn = ahora;
        }
    }

    public boolean isLeida() {
        return leidaEn != null;
    }

    public Long getId() {
        return id;
    }

    public Long getDestinatarioId() {
        return destinatarioId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getEntidad() {
        return entidad;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public Instant getLeidaEn() {
        return leidaEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
