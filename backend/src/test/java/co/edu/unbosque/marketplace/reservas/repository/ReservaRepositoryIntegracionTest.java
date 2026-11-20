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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de ocupación sobre PostgreSQL (SOF-33): la ventana y las reservas son semiabiertas. */
@SpringBootTest
class ReservaRepositoryIntegracionTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 12, 10);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 20);
    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private PublicacionRepository publicaciones;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EntityManager entidades;

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
    @DisplayName("Una PENDIENTE con el apartado vigente ocupa; una vencida (aunque siga PENDIENTE) ya no")
    void unaPendienteVencidaNoOcupa() {
        guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE, AHORA.plusSeconds(60));
        guardar(publicacionId, "2026-12-14", "2026-12-16", EstadoReserva.PENDIENTE, AHORA.minusSeconds(1));
        guardar(publicacionId, "2026-12-16", "2026-12-18", EstadoReserva.PENDIENTE, AHORA);

        assertThat(ocupantesDe(publicacionId)).extracting(Reserva::getLlegada)
                .containsExactly(LocalDate.of(2026, 12, 12));
    }

    @Test
    @DisplayName("Una CONFIRMADA ocupa aunque su fecha de apartado ya haya pasado; una VENCIDA no")
    void confirmadaOcupaYVencidaNo() {
        guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.CONFIRMADA, AHORA.minusSeconds(3600));
        guardar(publicacionId, "2026-12-14", "2026-12-16", EstadoReserva.VENCIDA, AHORA.plusSeconds(3600));

        assertThat(ocupantesDe(publicacionId)).extracting(Reserva::getLlegada)
                .containsExactly(LocalDate.of(2026, 12, 12));
    }

    @Test
    @DisplayName("El lote por varias publicaciones solo trae las reservas vigentes de las publicaciones pedidas")
    void elLoteSoloTraeLasPublicacionesPedidas() {
        guardar(otraPublicacionId, "2026-12-12", "2026-12-15", EstadoReserva.PENDIENTE);
        guardar(publicacionId, "2026-12-08", "2026-12-10", EstadoReserva.PENDIENTE);
        guardar(publicacionId, "2026-12-12", "2026-12-15", EstadoReserva.PENDIENTE, AHORA.minusSeconds(5));

        assertThat(ocupantesDe(publicacionId)).isEmpty();
        assertThat(reservas.findOcupantesDeVarias(Set.of(publicacionId, otraPublicacionId), DESDE, HASTA, AHORA))
                .extracting(Reserva::getPublicacionId).containsExactly(otraPublicacionId);
    }

    @Test
    @DisplayName("El reintento idempotente encuentra la reserva vigente del mismo huésped con las mismas fechas")
    void elReintentoIdempotenteEncuentraLaMismaReserva() {
        Reserva guardada = guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE);

        assertThat(identicas()).extracting(Reserva::getId).containsExactly(guardada.getId());
    }

    @Test
    @DisplayName("Una reserva cancelada o con el apartado vencido no cuenta como reintento idempotente")
    void canceladasYVencidasNoCuentanComoReintento() {
        guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.CANCELADA);
        guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.VENCIDA);
        guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE, AHORA.minusSeconds(1));

        assertThat(identicas()).isEmpty();
    }

    @Test
    @DisplayName("Una VENCIDA libera el rango en la restricción de exclusión: otra reserva puede tomar esas noches")
    void unaVencidaLiberaElRangoEnLaBaseDeDatos() {
        guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.VENCIDA);

        assertThat(guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE).getId()).isNotNull();
    }

    @Test
    @DisplayName("vencerCaducados pasa a VENCIDA solo las PENDIENTE cuyo apartado ya terminó")
    @Transactional
    void vencerCaducadosSoloTocaLasPendientesVencidas() {
        Reserva vencida = guardar(publicacionId, "2026-12-01", "2026-12-03", EstadoReserva.PENDIENTE,
                AHORA.minusSeconds(1));
        Reserva vigente = guardar(publicacionId, "2026-12-03", "2026-12-05", EstadoReserva.PENDIENTE,
                AHORA.plusSeconds(60));
        Reserva confirmada = guardar(publicacionId, "2026-12-05", "2026-12-07", EstadoReserva.CONFIRMADA,
                AHORA.minusSeconds(60));

        assertThat(reservas.vencerCaducados(AHORA)).isEqualTo(1);

        entidades.clear();
        assertThat(reservas.findById(vencida.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.VENCIDA);
        assertThat(reservas.findById(vigente.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(reservas.findById(confirmada.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    @DisplayName("vencerCaducadosEn solo libera los apartados vencidos de esa publicación que cruzan el rango")
    @Transactional
    void vencerCaducadosEnSeLimitaALaPublicacionYAlRango() {
        Reserva cruza = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE,
                AHORA.minusSeconds(1));
        Reserva fueraDelRango = guardar(publicacionId, "2026-12-20", "2026-12-22", EstadoReserva.PENDIENTE,
                AHORA.minusSeconds(1));
        Reserva otraPublicacion = guardar(otraPublicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE,
                AHORA.minusSeconds(1));

        assertThat(reservas.vencerCaducadosEn(publicacionId, DESDE, HASTA, AHORA)).isEqualTo(1);

        entidades.clear();
        assertThat(reservas.findById(cruza.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.VENCIDA);
        assertThat(reservas.findById(fueraDelRango.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(reservas.findById(otraPublicacion.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.PENDIENTE);
    }

    private List<Reserva> ocupantesDe(Long id) {
        return reservas.findOcupantes(id, DESDE, HASTA, AHORA);
    }

    private List<Reserva> identicas() {
        return reservas.findVigentesIdenticas(huespedId, publicacionId, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 14), AHORA);
    }

    private Reserva guardar(Long publicacionId, String llegada, String salida, EstadoReserva estado) {
        return guardar(publicacionId, llegada, salida, estado, AHORA.plusSeconds(900));
    }

    private Reserva guardar(Long publicacionId, String llegada, String salida, EstadoReserva estado,
            Instant venceEn) {
        Reserva reserva = Reserva.nueva(publicacionId, huespedId, LocalDate.parse(llegada), LocalDate.parse(salida),
                4, new BigDecimal("400000"), BigDecimal.ZERO, new BigDecimal("40000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("440000"), venceEn);
        if (estado != EstadoReserva.PENDIENTE) {
            ReflectionTestUtils.setField(reserva, "estado", estado);
        }
        return reservas.saveAndFlush(reserva);
    }

    private static DatosBorrador datos(String titulo) {
        return new DatosBorrador(titulo, null, null, null, null, null, null, null, null, null);
    }
}
