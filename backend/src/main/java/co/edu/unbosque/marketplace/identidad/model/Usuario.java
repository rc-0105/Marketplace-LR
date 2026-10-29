package co.edu.unbosque.marketplace.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Cuenta de la plataforma (RF-01). */
@Entity
@Table(name = "usuario")
public class Usuario {

    public static final String NOMBRE_ELIMINADO = "Usuario eliminado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 254, unique = true)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado;

    @Column(name = "motivo_restriccion", length = 500)
    private String motivoRestriccion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Usuario() {
        // Requerido por JPA.
    }

    private Usuario(String nombre, String correo, String contrasenaHash, Rol rol) {
        this.nombre = nombre;
        this.correo = correo;
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
        this.estado = EstadoCuenta.ACTIVA;
        this.creadoEn = Instant.now();
    }

    /** Toda cuenta nueva nace activa. */
    public static Usuario nuevo(String nombre, String correo, String contrasenaHash, Rol rol) {
        return new Usuario(nombre, correo, contrasenaHash, rol);
    }

    /** Solo una cuenta activa puede entrar; la desactivada y la restringida no. */
    public boolean puedeIniciarSesion() {
        return estado == EstadoCuenta.ACTIVA;
    }

    /** Recibe el hash ya calculado: la entidad nunca ve la contraseña en claro. */
    public void cambiarContrasena(String nuevoHash) {
        this.contrasenaHash = nuevoHash;
    }

    /** ACTIVA a RESTRINGIDA. El motivo es obligatorio y se guarda sin espacios sobrantes. */
    public void restringir(String motivo) {
        if (estado != EstadoCuenta.ACTIVA) {
            throw new IllegalStateException("Solo una cuenta activa se puede restringir");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la restricción es obligatorio");
        }
        this.estado = EstadoCuenta.RESTRINGIDA;
        this.motivoRestriccion = motivo.strip();
    }

    /** RESTRINGIDA a ACTIVA; el motivo deja de existir. */
    public void levantarRestriccion() {
        if (estado != EstadoCuenta.RESTRINGIDA) {
            throw new IllegalStateException("Solo una cuenta restringida se puede liberar");
        }
        this.estado = EstadoCuenta.ACTIVA;
        this.motivoRestriccion = null;
    }

    /** DESACTIVADA a ACTIVA. Una cuenta restringida no se reactiva por su cuenta. */
    public void reactivar() {
        if (estado != EstadoCuenta.DESACTIVADA) {
            throw new IllegalStateException("Solo una cuenta desactivada se puede reactivar");
        }
        this.estado = EstadoCuenta.ACTIVA;
    }

    /** ACTIVA a DESACTIVADA, por decisión de su dueño. */
    public void desactivar() {
        if (estado != EstadoCuenta.ACTIVA) {
            throw new IllegalStateException("Solo una cuenta activa se puede desactivar");
        }
        this.estado = EstadoCuenta.DESACTIVADA;
    }

    /**
     * ACTIVA a ELIMINADA (terminal): anonimiza la fila en lugar de borrarla. El hash inservible lo
     * genera quien llama, para que el modelo no dependa de la infraestructura de contraseñas.
     */
    public void eliminar(String hashInservible) {
        if (estado != EstadoCuenta.ACTIVA) {
            throw new IllegalStateException("Solo una cuenta activa se puede eliminar");
        }
        if (id == null) {
            throw new IllegalStateException("Una cuenta sin id no se puede anonimizar");
        }
        if (hashInservible == null || hashInservible.isBlank()) {
            throw new IllegalArgumentException("El hash inservible es obligatorio");
        }
        this.nombre = NOMBRE_ELIMINADO;
        this.correo = "eliminada-" + id + "@eliminada.invalid";
        this.contrasenaHash = hashInservible;
        this.motivoRestriccion = null;
        this.estado = EstadoCuenta.ELIMINADA;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public Rol getRol() {
        return rol;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public String getMotivoRestriccion() {
        return motivoRestriccion;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
