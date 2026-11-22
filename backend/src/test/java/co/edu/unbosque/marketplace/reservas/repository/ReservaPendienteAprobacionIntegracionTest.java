package co.edu.unbosque.marketplace.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Esquema de la solicitud de reserva (SOF-79, RF-52): PENDIENTE_APROBACION aparta noches con la restricción
 * de exclusión de PostgreSQL (ADR-3 intacto), el rechazo exige motivo y las notificaciones nuevas caben.
 */
@SpringBootTest
class ReservaPendienteAprobacionIntegracionTest {

    private static final LocalDate LLEGADA = LocalDate.now().plusDays(10);
    private static final LocalDate SALIDA = LLEGADA.plusDays(4);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PublicacionRepository publicaciones;
    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private PlatformTransactionManager transacciones;

    private TransactionTemplate transaccion;
    private Long huespedId;
    private Long publicacionId;

    @BeforeEach
    void preparar() {
        transaccion = new TransactionTemplate(transacciones);
        limpiar();
        Long anfitrionId = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", "hash", Rol.ANFITRION))
                .getId();
        huespedId = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash", Rol.HUESPED)).getId();
        publicacionId = publicaciones.save(Publicacion.borrador(anfitrionId,
                new DatosBorrador("Casa en la playa", null, null, null, null, null, null, null, null, null)))
                .getId();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM notificacion");
        jdbc.update("DELETE FROM reserva");
        publicaciones.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("Una PENDIENTE_APROBACION cabe en la columna estado (20) y aparta las noches: otra que cruza se rechaza")
    void unaSolicitudApartaLasNoches() {
        insertar("PENDIENTE_APROBACION", LLEGADA, SALIDA, null);

        for (String estado : List.of("PENDIENTE_APROBACION", "PENDIENTE", "CONFIRMADA", "EN_REVISION_MANUAL")) {
            assertThatThrownBy(() -> insertar(estado, LLEGADA.plusDays(1), SALIDA.plusDays(1), null))
                    .as("cruce con estado %s", estado)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("reserva_sin_cruce");
        }
    }

    @Test
    @DisplayName("Un periodo contiguo, o con la solicitud ya VENCIDA, CANCELADA o RECHAZADA, no choca con la restricción")
    void liberaLasNochesAlSalirDelEstado() {
        insertar("PENDIENTE_APROBACION", LLEGADA, SALIDA, null);
        insertar("PENDIENTE_APROBACION", SALIDA, SALIDA.plusDays(2), null);

        for (String estado : List.of("VENCIDA", "CANCELADA")) {
            insertar(estado, LLEGADA, SALIDA, null);
        }
        insertar("RECHAZADA", LLEGADA, SALIDA, "No disponible esas fechas");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva", Long.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("Una RECHAZADA exige motivo_rechazo, que admite hasta 500 caracteres")
    void elRechazoExigeMotivo() {
        assertThatThrownBy(() -> insertar("RECHAZADA", LLEGADA, SALIDA, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar("RECHAZADA", LLEGADA, SALIDA, "x".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertar("RECHAZADA", LLEGADA, SALIDA, "x".repeat(500));
    }

    @Test
    @DisplayName("Las cuatro notificaciones de solicitud caben en notificacion.tipo")
    void aceptaLosTiposDeSolicitud() {
        for (String tipo : List.of("SOLICITUD_RECIBIDA", "SOLICITUD_APROBADA", "SOLICITUD_RECHAZADA",
                "SOLICITUD_VENCIDA")) {
            jdbc.update("INSERT INTO notificacion (destinatario_id, tipo, mensaje, entidad, entidad_id)"
                    + " VALUES (?, ?, 'x', 'Reserva', 1)", huespedId, tipo);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("El repositorio cuenta como ocupante a la solicitud vigente y no a la que ya venció")
    void elRepositorioLaTrataComoOcupante() {
        Instant ahora = Instant.now();
        Long vigente = insertar("PENDIENTE_APROBACION", LLEGADA, SALIDA, null, ahora.plus(1, ChronoUnit.HOURS));

        assertThat(reservas.findOcupantes(publicacionId, LLEGADA, SALIDA, ahora)).extracting(Reserva::getId)
                .containsExactly(vigente);
        assertThat(reservas.findOcupantes(publicacionId, LLEGADA, SALIDA, ahora.plus(2, ChronoUnit.HOURS)))
                .isEmpty();
        assertThat(reservas.findById(vigente).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE_APROBACION);
    }

    @Test
    @DisplayName("El respaldo de la creación vence una solicitud agotada que cruza el rango")
    void elRespaldoVenceLaSolicitudAgotada() {
        Instant ahora = Instant.now();
        Long agotada = insertar("PENDIENTE_APROBACION", LLEGADA, SALIDA, null, ahora.minusSeconds(5));

        Integer vencidas = transaccion.execute(
                estado -> reservas.vencerCaducadosEn(publicacionId, LLEGADA, SALIDA, ahora));
        assertThat(vencidas).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, agotada))
                .isEqualTo("VENCIDA");
    }

    private Long insertar(String estado, LocalDate llegada, LocalDate salida, String motivoRechazo) {
        return insertar(estado, llegada, salida, motivoRechazo, Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private Long insertar(String estado, LocalDate llegada, LocalDate salida, String motivoRechazo,
            Instant venceEn) {
        return jdbc.queryForObject("""
                INSERT INTO reserva (publicacion_id, huesped_id, llegada, salida, estado, noches, precio_base,
                    tarifa_limpieza, tarifa_servicio, descuento, total_servicios_adicionales, total, vence_en,
                    motivo_rechazo)
                VALUES (?, ?, ?, ?, ?, 4, 400000, 30000, 40000, 0, 0, 470000, ?, ?) RETURNING id
                """, Long.class, publicacionId, huespedId, llegada, salida, estado, Timestamp.from(venceEn),
                motivoRechazo);
    }
}
