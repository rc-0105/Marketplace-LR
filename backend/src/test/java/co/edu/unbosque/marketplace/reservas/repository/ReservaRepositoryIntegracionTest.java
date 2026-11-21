package co.edu.unbosque.marketplace.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.reservas.model.EstadoPago;
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

    @Test
    @DisplayName("Los servicios adicionales elegidos se guardan y se leen de vuelta, también vacíos")
    void guardaLosServiciosAdicionalesElegidos() {
        Reserva sin = guardar(publicacionId, "2026-12-01", "2026-12-03", EstadoReserva.PENDIENTE);
        Reserva con = reservas.saveAndFlush(Reserva.nueva(publicacionId, huespedId, LocalDate.of(2026, 12, 3),
                LocalDate.of(2026, 12, 5), 2, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, Set.of(11L, 7L), AHORA.plusSeconds(900)));
        entidades.clear();

        assertThat(reservas.findById(sin.getId())).get().extracting(Reserva::getServiciosAdicionalesIds)
                .isEqualTo(Set.of());
        assertThat(reservas.findById(con.getId())).get().extracting(Reserva::getServiciosAdicionalesIds)
                .isEqualTo(Set.of(7L, 11L));
    }

    @Test
    @DisplayName("Una reserva nueva se guarda sin pago y sin identificador de transacción (SOF-34)")
    void unaReservaNuevaNaceSinPago() {
        Reserva guardada = guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE);
        entidades.clear();

        Reserva leida = reservas.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getEstadoPago()).isEqualTo(EstadoPago.SIN_PAGO);
        assertThat(leida.getTransaccionId()).isNull();
    }

    @Test
    @DisplayName("confirmarPago confirma una PENDIENTE y guarda la transacción; sobre otra ya no toca nada")
    @Transactional
    void confirmarPagoSoloConfirmaUnaPendiente() {
        Reserva pendiente = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.PENDIENTE);
        Reserva vencida = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.VENCIDA);

        assertThat(reservas.confirmarPago(pendiente.getId(), "pi_123")).isEqualTo(1);
        assertThat(reservas.confirmarPago(pendiente.getId(), "pi_otro")).isZero();
        assertThat(reservas.confirmarPago(vencida.getId(), "pi_456")).isZero();

        entidades.clear();
        Reserva confirmada = reservas.findById(pendiente.getId()).orElseThrow();
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(confirmada.getEstadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(confirmada.getTransaccionId()).isEqualTo("pi_123");
        Reserva sinTocar = reservas.findById(vencida.getId()).orElseThrow();
        assertThat(sinTocar.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(sinTocar.getEstadoPago()).isEqualTo(EstadoPago.SIN_PAGO);
        assertThat(sinTocar.getTransaccionId()).isNull();
    }

    @Test
    @DisplayName("rechazarPago cancela la PENDIENTE y libera las noches para otra reserva")
    @Transactional
    void rechazarPagoCancelaYLiberaLasNoches() {
        Reserva pendiente = guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE);

        assertThat(reservas.rechazarPago(pendiente.getId())).isEqualTo(1);
        assertThat(reservas.rechazarPago(pendiente.getId())).isZero();

        entidades.clear();
        Reserva rechazada = reservas.findById(pendiente.getId()).orElseThrow();
        assertThat(rechazada.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(rechazada.getEstadoPago()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE).getId()).isNotNull();
    }

    @Test
    @DisplayName("registrarErrorPago deja la reserva PENDIENTE con el pago en ERROR, sin tocar un pago ya resuelto")
    @Transactional
    void registrarErrorPagoMantieneLaPendiente() {
        Reserva pendiente = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.PENDIENTE);
        Reserva rechazada = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE);
        reservas.rechazarPago(rechazada.getId());

        assertThat(reservas.registrarErrorPago(pendiente.getId())).isEqualTo(1);
        assertThat(reservas.registrarErrorPago(rechazada.getId())).isZero();

        entidades.clear();
        Reserva conError = reservas.findById(pendiente.getId()).orElseThrow();
        assertThat(conError.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(conError.getEstadoPago()).isEqualTo(EstadoPago.ERROR);
        assertThat(reservas.findById(rechazada.getId())).get().extracting(Reserva::getEstadoPago)
                .isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    @DisplayName("Conciliación: lista solo las reservas con el pago en ERROR, en orden")
    @Transactional
    void listaLasReservasConElPagoEnError() {
        Reserva primera = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.PENDIENTE);
        Reserva sinError = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE);
        Reserva segunda = guardar(publicacionId, "2026-12-14", "2026-12-16", EstadoReserva.PENDIENTE);
        reservas.registrarErrorPago(primera.getId());
        reservas.registrarErrorPago(segunda.getId());
        entidades.clear();

        assertThat(reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)).extracting(Reserva::getId)
                .containsExactly(primera.getId(), segunda.getId())
                .doesNotContain(sinError.getId());
    }

    @Test
    @DisplayName("Conciliación: marcarReembolsado guarda la transacción de una reserva no confirmada con el pago en ERROR")
    @Transactional
    void marcarReembolsadoGuardaLaTransaccion() {
        Reserva vencida = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.VENCIDA);
        Reserva sinError = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.VENCIDA);
        assertThat(reservas.registrarErrorPago(vencida.getId())).isEqualTo(1);

        assertThat(reservas.marcarReembolsado(vencida.getId(), "pi_9")).isEqualTo(1);
        assertThat(reservas.marcarReembolsado(sinError.getId(), "pi_10")).isZero();

        entidades.clear();
        Reserva reembolsada = reservas.findById(vencida.getId()).orElseThrow();
        assertThat(reembolsada.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(reembolsada.getEstadoPago()).isEqualTo(EstadoPago.REEMBOLSADO);
        assertThat(reembolsada.getTransaccionId()).isEqualTo("pi_9");
        assertThat(reservas.findById(sinError.getId())).get().extracting(Reserva::getEstadoPago)
                .isEqualTo(EstadoPago.SIN_PAGO);
    }

    @Test
    @DisplayName("Conciliación: marcarNoCobrado cierra un pago en ERROR sin transacción y no toca una confirmada")
    @Transactional
    void marcarNoCobradoCierraElError() {
        Reserva pendiente = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.PENDIENTE);
        Reserva confirmada = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE);
        reservas.registrarErrorPago(pendiente.getId());
        reservas.confirmarPago(confirmada.getId(), "pi_1");

        assertThat(reservas.marcarNoCobrado(pendiente.getId())).isEqualTo(1);
        assertThat(reservas.marcarNoCobrado(confirmada.getId())).isZero();

        entidades.clear();
        assertThat(reservas.findById(pendiente.getId())).get().extracting(Reserva::getEstadoPago)
                .isEqualTo(EstadoPago.NO_COBRADO);
        assertThat(reservas.findById(confirmada.getId())).get().extracting(Reserva::getEstadoPago)
                .isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    @DisplayName("registrarErrorPago marca ERROR aunque el apartado haya vencido mientras se esperaba a la pasarela")
    @Transactional
    void registrarErrorPagoAlcanzaAUnaReservaYaVencida() {
        Reserva vencida = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.VENCIDA);
        Reserva confirmada = guardar(publicacionId, "2026-12-12", "2026-12-14", EstadoReserva.PENDIENTE);
        reservas.confirmarPago(confirmada.getId(), "pi_1");

        assertThat(reservas.registrarErrorPago(vencida.getId())).isEqualTo(1);
        assertThat(reservas.registrarErrorPago(confirmada.getId())).isZero();

        entidades.clear();
        assertThat(reservas.findById(vencida.getId())).get().extracting(Reserva::getEstadoPago)
                .isEqualTo(EstadoPago.ERROR);
    }

    @Test
    @DisplayName("cancelar cancela una CONFIRMADA y libera las noches; sobre otra ya no toca nada")
    @Transactional
    void cancelarSoloCancelaUnaConfirmada() {
        Reserva confirmada = guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.CONFIRMADA);
        Reserva pendiente = guardar(publicacionId, "2026-12-14", "2026-12-16", EstadoReserva.PENDIENTE);

        assertThat(reservas.cancelar(confirmada.getId())).isEqualTo(1);
        assertThat(reservas.cancelar(confirmada.getId())).isZero();
        assertThat(reservas.cancelar(pendiente.getId())).isZero();

        entidades.clear();
        assertThat(reservas.findById(confirmada.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reservas.findById(pendiente.getId())).get().extracting(Reserva::getEstado)
                .isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.PENDIENTE).getId()).isNotNull();
    }

    @Test
    @DisplayName("SOF-36: findByPublicacionIdInOrderByLlegadaAscIdAsc trae todos los estados, ordenadas por"
            + " llegada ascendente, sin separar pasadas de futuras (D2/D3)")
    void findByPublicacionIdInTraeTodosLosEstadosOrdenadosPorLlegadaAscendente() {
        Reserva pasada = guardar(publicacionId, "2026-11-01", "2026-11-05", EstadoReserva.CANCELADA);
        Reserva pendiente = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.PENDIENTE);
        Reserva confirmada = guardar(publicacionId, "2026-12-15", "2026-12-18", EstadoReserva.CONFIRMADA);
        Reserva vencida = guardar(publicacionId, "2026-12-20", "2026-12-22", EstadoReserva.VENCIDA);
        // De otra publicación: no debe aparecer al pedir solo publicacionId.
        guardar(otraPublicacionId, "2026-12-01", "2026-12-03", EstadoReserva.CONFIRMADA);

        List<Reserva> encontradas = reservas.findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(publicacionId));

        // Pasadas y futuras se intercalan por fecha de llegada: no hay un segundo grupo al final (D3).
        assertThat(encontradas).extracting(Reserva::getId)
                .containsExactly(pasada.getId(), pendiente.getId(), confirmada.getId(), vencida.getId());
        assertThat(encontradas).extracting(Reserva::getEstado)
                .containsExactly(EstadoReserva.CANCELADA, EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
                        EstadoReserva.VENCIDA);
    }

    @Test
    @DisplayName("SOF-36: findByPublicacionIdInOrderByLlegadaAscIdAsc consulta varias publicaciones a la vez y"
            + " desempata por id cuando la llegada es la misma")
    void findByPublicacionIdInConVariasPublicacionesYMismaLlegada() {
        Reserva primera = guardar(publicacionId, "2026-12-10", "2026-12-12", EstadoReserva.PENDIENTE);
        Reserva segunda = guardar(publicacionId, "2026-12-10", "2026-12-14", EstadoReserva.CONFIRMADA);
        Reserva deOtra = guardar(otraPublicacionId, "2026-12-01", "2026-12-03", EstadoReserva.CONFIRMADA);

        List<Reserva> encontradas = reservas
                .findByPublicacionIdInOrderByLlegadaAscIdAsc(Set.of(publicacionId, otraPublicacionId));

        assertThat(encontradas).extracting(Reserva::getId)
                .containsExactly(deOtra.getId(), primera.getId(), segunda.getId());
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
                BigDecimal.ZERO, new BigDecimal("440000"), Set.of(), venceEn);
        if (estado != EstadoReserva.PENDIENTE) {
            ReflectionTestUtils.setField(reserva, "estado", estado);
        }
        return reservas.saveAndFlush(reserva);
    }

    private static DatosBorrador datos(String titulo) {
        return new DatosBorrador(titulo, null, null, null, null, null, null, null, null, null);
    }
}
