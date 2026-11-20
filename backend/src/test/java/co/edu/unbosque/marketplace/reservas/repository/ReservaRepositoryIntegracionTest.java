package co.edu.unbosque.marketplace.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/** Consultas de ocupación sobre PostgreSQL (SOF-33): la ventana y las reservas son semiabiertas. */
@SpringBootTest
class ReservaRepositoryIntegracionTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 12, 10);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 20);

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private UsuarioRepository usuarios;

    private Long publicacionId;
    private Long otraPublicacionId;
    private Long huespedId;

    @BeforeEach
    void preparar() {
        publicaciones.deleteAll();
        usuarios.deleteAll();
        Long anfitrionId = usuarios.save(Usuario.nuevo("Luis Pardo", "luis@correo.com", "hash", Rol.ANFITRION))
                .getId();
        huespedId = usuarios.save(Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash", Rol.HUESPED)).getId();
        publicacionId = publicaciones.save(Publicacion.borrador(anfitrionId, datos("Casa uno"))).getId();
        otraPublicacionId = publicaciones.save(Publicacion.borrador(anfitrionId, datos("Casa dos"))).getId();
    }

    // Las reservas no caen en cascada; si quedan, otras pruebas no pueden borrar sus usuarios.
    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        publicaciones.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("Una reserva dentro de la ventana se devuelve y la que solo la toca por el borde no")
    void excluyeLasReservasQueSoloTocanLaVentana() {
        guardar(publicacionId, "2026-12-01", "2026-12-10", EstadoReserva.PENDIENTE);
        guardar(publicacionId, "2026-12-12", "2026-12-15", EstadoReserva.PENDIENTE);
        guardar(publicacionId, "2026-12-20", "2026-12-25", EstadoReserva.PENDIENTE);

        List<Reserva> encontradas = ocupantesDe(publicacionId);

        assertThat(encontradas).extracting(Reserva::getLlegada).containsExactly(LocalDate.of(2026, 12, 12));
    }

    @Test
    @DisplayName("Una reserva CANCELADA no ocupa noches aunque esté dentro de la ventana")
    void unaReservaCanceladaNoOcupa() {
        guardar(publicacionId, "2026-12-12", "2026-12-15", EstadoReserva.CANCELADA);

        assertThat(ocupantesDe(publicacionId)).isEmpty();
    }

    @Test
    @DisplayName("PENDIENTE y CONFIRMADA ocupan noches; solo CONFIRMADA bloquea un periodo")
    void soloConfirmadaBloqueaUnPeriodo() {
        guardar(publicacionId, "2026-12-12", "2026-12-15", EstadoReserva.PENDIENTE);

        assertThat(ocupantesDe(publicacionId)).hasSize(1);
        assertThat(reservas.findFirstByPublicacionIdAndEstadoAndLlegadaLessThanAndSalidaGreaterThan(publicacionId,
                EstadoReserva.CONFIRMADA, HASTA, DESDE)).isEmpty();

        guardar(publicacionId, "2026-12-16", "2026-12-18", EstadoReserva.CONFIRMADA);

        Optional<Reserva> confirmada = reservas.findFirstByPublicacionIdAndEstadoAndLlegadaLessThanAndSalidaGreaterThan(
                publicacionId, EstadoReserva.CONFIRMADA, HASTA, DESDE);
        assertThat(confirmada).isPresent();
        assertThat(confirmada.get().getLlegada()).isEqualTo(LocalDate.of(2026, 12, 16));
    }

    @Test
    @DisplayName("El lote por varias publicaciones solo trae las reservas de las publicaciones pedidas")
    void elLoteSoloTraeLasPublicacionesPedidas() {
        guardar(otraPublicacionId, "2026-12-12", "2026-12-15", EstadoReserva.PENDIENTE);
        guardar(publicacionId, "2026-12-08", "2026-12-10", EstadoReserva.PENDIENTE);

        assertThat(ocupantesDe(publicacionId)).isEmpty();
        assertThat(reservas.findByPublicacionIdInAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(
                Set.of(publicacionId, otraPublicacionId), Set.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA),
                HASTA, DESDE)).extracting(Reserva::getPublicacionId).containsExactly(otraPublicacionId);
    }

    @Test
    @DisplayName("El reintento idempotente encuentra la reserva no cancelada del mismo huésped con las mismas fechas")
    void elReintentoIdempotenteEncuentraLaMismaReserva() {
        Reserva guardada = guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE);

        Optional<Reserva> encontrada = reservas.findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(
                huespedId, publicacionId, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14),
                EstadoReserva.CANCELADA);

        assertThat(encontrada).map(Reserva::getId).contains(guardada.getId());
    }

    @Test
    @DisplayName("Una reserva cancelada no cuenta como reintento idempotente")
    void unaReservaCanceladaNoCuentaComoReintento() {
        guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.CANCELADA);

        assertThat(reservas.findFirstByHuespedIdAndPublicacionIdAndLlegadaAndSalidaAndEstadoNot(huespedId,
                publicacionId, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14), EstadoReserva.CANCELADA))
                .isEmpty();
    }

    private List<Reserva> ocupantesDe(Long id) {
        return reservas.findByPublicacionIdAndEstadoInAndLlegadaLessThanAndSalidaGreaterThan(id,
                Set.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA), HASTA, DESDE);
    }

    private Reserva guardar(Long publicacionId, String llegada, String salida, EstadoReserva estado) {
        Reserva reserva = Reserva.nueva(publicacionId, huespedId, LocalDate.parse(llegada), LocalDate.parse(salida),
                4, new BigDecimal("400000"), BigDecimal.ZERO, new BigDecimal("40000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("440000"));
        if (estado != EstadoReserva.PENDIENTE) {
            ReflectionTestUtils.setField(reserva, "estado", estado);
        }
        return reservas.saveAndFlush(reserva);
    }

    private static DatosBorrador datos(String titulo) {
        return new DatosBorrador(titulo, null, null, null, null, null, null, null, null, null);
    }
}
