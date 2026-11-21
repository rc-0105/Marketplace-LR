package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cierre de reservas con la estancia terminada contra PostgreSQL real (SOF-78, HU-33, RF-50): confirma
 * que las tres reglas de negocio (AC1, AC2, AC3) valen de punta a punta, no solo contra el mock del
 * repositorio.
 */
@SpringBootTest
class CierreReservasServiceIntegracionTest {

    // El servicio usa el reloj real de la aplicación (ConfiguracionJwt.reloj()); las fechas de cada
    // caso se calculan relativas a hoy para no depender de qué día corra la prueba.
    private static final Instant AHORA = Instant.now();
    private static final LocalDate HOY = LocalDate.now();

    @Autowired
    private CierreReservasService cierre;

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EntityManager entidades;

    private Long publicacionId;
    private Long huespedId;

    @BeforeEach
    void preparar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
        Long anfitrionId = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", "hash", Rol.ANFITRION))
                .getId();
        huespedId = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash", Rol.HUESPED)).getId();
        publicacionId = publicaciones.save(Publicacion.borrador(anfitrionId, datos("Casa de prueba"))).getId();
    }

    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("AC1: una CONFIRMADA con salida pasada queda COMPLETADA al correr el cierre")
    void unaConfirmadaConSalidaPasadaQuedaCompletada() {
        Reserva terminada = guardar(HOY.minusDays(20), HOY.minusDays(16), EstadoReserva.CONFIRMADA);

        cierre.cerrarReservasConEstanciaTerminada();

        entidades.clear();
        assertThat(reservas.findById(terminada.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.COMPLETADA);
    }

    @Test
    @DisplayName("Una CONFIRMADA con salida futura sigue CONFIRMADA tras correr el cierre")
    void unaConfirmadaConSalidaFuturaSigueConfirmada() {
        Reserva futura = guardar(HOY.plusDays(20), HOY.plusDays(24), EstadoReserva.CONFIRMADA);

        cierre.cerrarReservasConEstanciaTerminada();

        entidades.clear();
        assertThat(reservas.findById(futura.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    @DisplayName("AC3: una CANCELADA con salida pasada sigue CANCELADA tras correr el cierre")
    void unaCanceladaConSalidaPasadaSigueCancelada() {
        Reserva cancelada = guardar(HOY.minusDays(20), HOY.minusDays(16), EstadoReserva.CANCELADA);

        cierre.cerrarReservasConEstanciaTerminada();

        entidades.clear();
        assertThat(reservas.findById(cancelada.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    @DisplayName("Una PENDIENTE con salida pasada no está en el alcance del cierre: no la toca")
    void unaPendienteConSalidaPasadaNoSeToca() {
        Reserva pendiente = guardar(HOY.minusDays(20), HOY.minusDays(16), EstadoReserva.PENDIENTE,
                AHORA.minusSeconds(1));

        cierre.cerrarReservasConEstanciaTerminada();

        entidades.clear();
        assertThat(reservas.findById(pendiente.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.PENDIENTE);
    }

    @Test
    @DisplayName("Una COMPLETADA ya no ocupa noches: la publicación queda libre para otra reserva en esas fechas")
    void unaCompletadaYaNoOcupaNochesParaOtraReserva() {
        LocalDate llegada = HOY.minusDays(20);
        LocalDate salida = HOY.minusDays(16);
        guardar(llegada, salida, EstadoReserva.CONFIRMADA);

        cierre.cerrarReservasConEstanciaTerminada();
        entidades.clear();

        List<Reserva> ocupantes = reservas.findOcupantes(publicacionId, llegada, salida, AHORA);
        assertThat(ocupantes).isEmpty();
        Reserva otra = guardar(llegada, salida, EstadoReserva.PENDIENTE);
        assertThat(otra.getId()).isNotNull();
    }

    private Reserva guardar(LocalDate llegada, LocalDate salida, EstadoReserva estado) {
        return guardar(llegada, salida, estado, AHORA.plusSeconds(900));
    }

    private Reserva guardar(LocalDate llegada, LocalDate salida, EstadoReserva estado, Instant venceEn) {
        Reserva reserva = Reserva.nueva(publicacionId, huespedId, llegada, salida, 4, new BigDecimal("400000"),
                BigDecimal.ZERO, new BigDecimal("40000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("440000"), Set.of(), venceEn);
        if (estado != EstadoReserva.PENDIENTE) {
            ReflectionTestUtils.setField(reserva, "estado", estado);
        }
        return reservas.saveAndFlush(reserva);
    }

    private static DatosBorrador datos(String titulo) {
        return new DatosBorrador(titulo, null, null, null, null, null, null, null, null, null);
    }
}
