package co.edu.unbosque.marketplace.alojamientos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Archivo de imagen subido para una publicación (RF-19). La publicación no lo referencia por relación: guarda
 * su URL pública, igual que guarda las URL externas.
 */
@Entity
@Table(name = "imagen_archivo")
public class ImagenArchivo {

    @Id
    private UUID id;

    @Column(name = "publicacion_id", nullable = false, updatable = false)
    private Long publicacionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private TipoImagen tipo;

    @Column(nullable = false, updatable = false)
    private byte[] contenido;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected ImagenArchivo() {
        // Requerido por JPA.
    }

    public ImagenArchivo(UUID id, Long publicacionId, TipoImagen tipo, byte[] contenido) {
        this.id = id;
        this.publicacionId = publicacionId;
        this.tipo = tipo;
        this.contenido = contenido;
        this.creadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public TipoImagen getTipo() {
        return tipo;
    }

    public byte[] getContenido() {
        return contenido;
    }
}
