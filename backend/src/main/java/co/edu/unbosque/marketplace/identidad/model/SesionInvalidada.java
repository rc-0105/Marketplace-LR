package co.edu.unbosque.marketplace.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Token cerrado antes de vencer (RF-03). La fila se inserta con SQL nativo, ver el repositorio. */
@Entity
@Table(name = "sesion_invalidada")
public class SesionInvalidada {

    @Id
    @Column(length = 36)
    private String jti;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "invalidado_en", nullable = false, insertable = false, updatable = false)
    private Instant invalidadoEn;

    protected SesionInvalidada() {
        // Requerido por JPA.
    }

    public String getJti() {
        return jti;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getInvalidadoEn() {
        return invalidadoEn;
    }
}
