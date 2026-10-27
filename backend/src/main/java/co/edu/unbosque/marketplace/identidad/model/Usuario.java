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

    /** Gancho para SOF-15 y SOF-17: hoy toda cuenta activa puede entrar. */
    public boolean puedeIniciarSesion() {
        return estado == EstadoCuenta.ACTIVA;
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

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
