package co.edu.unbosque.marketplace.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Código de un solo uso para restablecer la contraseña (RF-05). Solo se guarda su hash. */
@Entity
@Table(name = "token_recuperacion")
public class TokenRecuperacion {

    /** Con 6 dígitos, limitar los intentos es lo que impide adivinar el código. */
    public static final int MAXIMO_INTENTOS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 64, updatable = false)
    private String codigoHash;

    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected TokenRecuperacion() {
        // Requerido por JPA.
    }

    private TokenRecuperacion(Long usuarioId, String codigoHash, Instant venceEn) {
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.venceEn = venceEn;
        this.creadoEn = Instant.now();
    }

    public static TokenRecuperacion emitir(Long usuarioId, String codigoHash, Instant venceEn) {
        return new TokenRecuperacion(usuarioId, codigoHash, venceEn);
    }

    /** Sirve mientras no se haya usado, no haya vencido y no se hayan agotado los intentos. */
    public boolean estaVigente(Instant ahora) {
        return usadoEn == null && ahora.isBefore(venceEn) && intentosFallidos < MAXIMO_INTENTOS;
    }

    public void registrarIntentoFallido() {
        intentosFallidos++;
    }

    /** Lo deja inservible, ya sea porque se usó o porque se pidió uno nuevo. */
    public void marcarUsado(Instant ahora) {
        usadoEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public Instant getVenceEn() {
        return venceEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
