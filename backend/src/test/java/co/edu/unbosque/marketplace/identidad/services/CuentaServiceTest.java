package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasFuturas;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.UsuariosDePrueba;
import co.edu.unbosque.marketplace.identidad.exceptions.ConfirmacionInvalidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.OperacionCuentaNoPermitidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.ReservasFuturasException;
import co.edu.unbosque.marketplace.identidad.exceptions.UsuarioNoEncontradoException;
import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class CuentaServiceTest {

    private static final Long ADMIN = 1L;
    private static final Long AFECTADO = 7L;
    private static final String MOTIVO = "Pagos en disputa";
    private static final String CONTRASENA = "Segura#2026";
    private static final String INTENTO = "Otra#Clave1";
    private static final String OPERACION = "operacion";
    private static final String MOTIVO_FALLO = "motivo";
    private static final String HASH_NUEVO = "$2a$10$nuevo";
    private static final String BLOQUEANTES = "reservasBloqueantes";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final PasswordEncoder codificador = mock(PasswordEncoder.class);
    private final ConsultaReservasFuturas reservas = mock(ConsultaReservasFuturas.class);
    private CuentaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CuentaService(usuarios, publicador, codificador, reservas);
    }

    @Test
    @DisplayName("Restringir una cuenta activa la guarda restringida con el motivo y audita la operación")
    void restringeCuentaActiva() {
        Usuario ana = UsuariosDePrueba.activa(AFECTADO);
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.of(ana));

        servicio.restringir(AFECTADO, MOTIVO, ADMIN);

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.RESTRINGIDA);
        assertThat(ana.getMotivoRestriccion()).isEqualTo(MOTIVO);
        verify(usuarios).save(ana);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.ACCION_ADMINISTRATIVA);
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.usuario()).isEqualTo("1");
        assertThat(evento.entidad()).isEqualTo("Usuario");
        assertThat(evento.entidadId()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry(OPERACION, "restringir_cuenta");
    }

    @Test
    @DisplayName("Un administrador no puede restringirse a sí mismo y ni siquiera se consulta la base")
    void rechazaAutorrestriccion() {
        assertThatThrownBy(() -> servicio.restringir(ADMIN, MOTIVO, ADMIN))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class);

        verifyNoInteractions(usuarios, publicador);
    }

    @Test
    @DisplayName("Tampoco puede levantarse la restricción a sí mismo")
    void rechazaAutoLevantamiento() {
        assertThatThrownBy(() -> servicio.levantarRestriccion(ADMIN, ADMIN))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class);

        verifyNoInteractions(usuarios, publicador);
    }

    @Test
    @DisplayName("Un usuario inexistente da 404 al restringir y al levantar, sin evento")
    void usuarioInexistente() {
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restringir(AFECTADO, MOTIVO, ADMIN))
                .isInstanceOf(UsuarioNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.levantarRestriccion(AFECTADO, ADMIN))
                .isInstanceOf(UsuarioNoEncontradoException.class);

        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("Restringir una cuenta ya restringida o desactivada da 409 y no cambia el motivo")
    void noRestringeCuentaQueNoEstaActiva() {
        Usuario restringida = UsuariosDePrueba.restringida(AFECTADO, "Motivo previo");
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.of(restringida));
        when(usuarios.findById(8L)).thenReturn(Optional.of(UsuariosDePrueba.desactivada(8L)));

        assertThatThrownBy(() -> servicio.restringir(AFECTADO, MOTIVO, ADMIN))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.restringir(8L, MOTIVO, ADMIN))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class);

        assertThat(restringida.getMotivoRestriccion()).isEqualTo("Motivo previo");
        verify(usuarios, never()).save(restringida);
        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("Un administrador puede restringir a otro administrador")
    void restringeOtroAdministrador() {
        Usuario otroAdmin = UsuariosDePrueba.activa(AFECTADO);
        ReflectionTestUtils.setField(otroAdmin, "rol", Rol.ADMINISTRADOR);
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.of(otroAdmin));

        servicio.restringir(AFECTADO, MOTIVO, ADMIN);

        assertThat(otroAdmin.getEstado()).isEqualTo(EstadoCuenta.RESTRINGIDA);
    }

    @Test
    @DisplayName("Levantar la restricción deja la cuenta activa, sin motivo, y audita la operación")
    void levantaRestriccion() {
        Usuario ana = UsuariosDePrueba.restringida(AFECTADO, MOTIVO);
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.of(ana));

        servicio.levantarRestriccion(AFECTADO, ADMIN);

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(ana.getMotivoRestriccion()).isNull();
        verify(usuarios).save(ana);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.ACCION_ADMINISTRATIVA);
        assertThat(evento.usuario()).isEqualTo("1");
        assertThat(evento.entidadId()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry(OPERACION, "levantar_restriccion");
    }

    @Test
    @DisplayName("Levantar la restricción de una cuenta activa o desactivada da 409 sin evento")
    void noLevantaRestriccionDeCuentaNoRestringida() {
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.of(UsuariosDePrueba.activa(AFECTADO)));
        when(usuarios.findById(8L)).thenReturn(Optional.of(UsuariosDePrueba.desactivada(8L)));

        assertThatThrownBy(() -> servicio.levantarRestriccion(AFECTADO, ADMIN))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.levantarRestriccion(8L, ADMIN))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class);

        verifyNoInteractions(publicador);
    }

    @Test
    @DisplayName("Desactivar con la contraseña correcta y sin reservas guarda la cuenta desactivada y audita el éxito")
    void desactivaCuentaDelDueno() {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));
        contrasenaCorrecta(ana);
        when(reservas.contarConfirmadasFuturas(AFECTADO)).thenReturn(0L);

        servicio.desactivar(AFECTADO, CONTRASENA);

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
        verify(usuarios).save(ana);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.entidad()).isEqualTo("Usuario");
        assertThat(evento.entidadId()).isEqualTo("7");
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, Baja.DESACTIVAR.operacion));
        sinDatosPersonales(evento);
    }

    @Test
    @DisplayName("Eliminar anonimiza la fila con un hash nuevo que no deriva de la contraseña y audita el éxito")
    void eliminaCuentaDelDueno() {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));
        contrasenaCorrecta(ana);
        when(reservas.contarConfirmadasFuturas(AFECTADO)).thenReturn(0L);
        when(codificador.encode(anyString())).thenReturn(HASH_NUEVO);

        servicio.eliminar(AFECTADO, CONTRASENA);

        ArgumentCaptor<String> aleatorio = ArgumentCaptor.forClass(String.class);
        verify(codificador).encode(aleatorio.capture());
        assertThat(aleatorio.getValue()).isNotBlank().isNotEqualTo(CONTRASENA);
        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ELIMINADA);
        assertThat(ana.getNombre()).isEqualTo("Usuario eliminado");
        assertThat(ana.getCorreo()).isEqualTo("eliminada-7@eliminada.invalid");
        assertThat(ana.getContrasenaHash()).isEqualTo(HASH_NUEVO);
        assertThat(ana.getMotivoRestriccion()).isNull();
        verify(usuarios).save(ana);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, Baja.ELIMINAR.operacion));
        sinDatosPersonales(evento);
    }

    @ParameterizedTest
    @EnumSource(Baja.class)
    @DisplayName("Una contraseña incorrecta da 403, audita el fallo y no consulta reservas ni guarda")
    void rechazaContrasenaIncorrecta(Baja baja) {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));

        assertThatThrownBy(() -> baja.ejecutar(servicio, INTENTO))
                .isInstanceOf(ConfirmacionInvalidaException.class);

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(usuarios, never()).save(ana);
        verifyNoInteractions(reservas);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, baja.operacion),
                Map.entry(MOTIVO_FALLO, "credenciales_invalidas"));
        sinDatosPersonales(evento);
    }

    @ParameterizedTest
    @MethodSource("bajasPorEstadoNoActivo")
    @DisplayName("Una cuenta que no está activa da 409, audita el fallo y no toca el puerto de reservas")
    void rechazaCuentaQueNoEstaActiva(Baja baja, EstadoCuenta estado) {
        Usuario ana = dueno(UsuariosDePrueba.conEstado(AFECTADO, estado,
                estado == EstadoCuenta.RESTRINGIDA ? MOTIVO : null));
        contrasenaCorrecta(ana);

        assertThatThrownBy(() -> baja.ejecutar(servicio, CONTRASENA))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class)
                .hasMessage("Solo se puede " + baja.verbo + " una cuenta activa.");

        assertThat(ana.getEstado()).isEqualTo(estado);
        verify(usuarios, never()).save(ana);
        verifyNoInteractions(reservas);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, baja.operacion),
                Map.entry(MOTIVO_FALLO, "estado_no_permitido"));
        sinDatosPersonales(evento);
    }

    @ParameterizedTest
    @MethodSource("bajasPorReservasPendientes")
    @DisplayName("Con reservas pendientes da 409 con el conteo, audita el fallo con el número y no guarda")
    void rechazaCuentaConReservasFuturas(Baja baja, long pendientes) {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));
        contrasenaCorrecta(ana);
        when(reservas.contarConfirmadasFuturas(AFECTADO)).thenReturn(pendientes);

        assertThatThrownBy(() -> baja.ejecutar(servicio, CONTRASENA))
                .isInstanceOfSatisfying(ReservasFuturasException.class, error -> {
                    assertThat(error.getPropiedades()).containsEntry(BLOQUEANTES, pendientes);
                    assertThat(error.getMessage()).contains("(" + pendientes + ")");
                });

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(usuarios, never()).save(ana);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, baja.operacion),
                Map.entry(MOTIVO_FALLO, "reservas_futuras"), Map.entry(BLOQUEANTES, pendientes));
        sinDatosPersonales(evento);
    }

    @ParameterizedTest
    @EnumSource(Baja.class)
    @DisplayName("El orden es contraseña, reservas y recién entonces se guarda")
    void verificaEnOrden(Baja baja) {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));
        contrasenaCorrecta(ana);
        when(reservas.contarConfirmadasFuturas(AFECTADO)).thenReturn(0L);
        when(codificador.encode(anyString())).thenReturn(HASH_NUEVO);

        baja.ejecutar(servicio, CONTRASENA);

        InOrder orden = inOrder(codificador, reservas, usuarios);
        orden.verify(codificador).matches(CONTRASENA, "hash-real");
        orden.verify(reservas).contarConfirmadasFuturas(AFECTADO);
        orden.verify(usuarios).save(ana);
    }

    @ParameterizedTest
    @EnumSource(Baja.class)
    @DisplayName("Un usuario inexistente da 404, sin evento ni consulta de reservas")
    void bajaDeUsuarioInexistente(Baja baja) {
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> baja.ejecutar(servicio, CONTRASENA))
                .isInstanceOf(UsuarioNoEncontradoException.class);

        verifyNoInteractions(publicador, reservas);
    }

    private static Stream<Arguments> bajasPorEstadoNoActivo() {
        return Stream.of(Baja.values()).flatMap(baja -> Stream.of(EstadoCuenta.RESTRINGIDA,
                EstadoCuenta.DESACTIVADA, EstadoCuenta.ELIMINADA).map(estado -> Arguments.of(baja, estado)));
    }

    private static Stream<Arguments> bajasPorReservasPendientes() {
        return Stream.of(Baja.values()).flatMap(baja -> Stream.of(2L, 3L).map(n -> Arguments.of(baja, n)));
    }

    // Las dos bajas del dueño comparten reglas; cada una aporta su operación auditada y su verbo.
    private enum Baja {
        DESACTIVAR("desactivar_cuenta", "desactivar") {
            @Override
            void ejecutar(CuentaService servicio, String contrasena) {
                servicio.desactivar(AFECTADO, contrasena);
            }
        },
        ELIMINAR("eliminar_cuenta", "eliminar") {
            @Override
            void ejecutar(CuentaService servicio, String contrasena) {
                servicio.eliminar(AFECTADO, contrasena);
            }
        };

        private final String operacion;
        private final String verbo;

        Baja(String operacion, String verbo) {
            this.operacion = operacion;
            this.verbo = verbo;
        }

        abstract void ejecutar(CuentaService servicio, String contrasena);
    }

    private Usuario dueno(Usuario usuario) {
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private void contrasenaCorrecta(Usuario usuario) {
        when(codificador.matches(CONTRASENA, usuario.getContrasenaHash())).thenReturn(true);
    }

    // Ni la contraseña (correcta o intentada), ni el correo, el nombre o el hash llegan a la auditoría.
    private void sinDatosPersonales(EventoCritico evento) {
        assertThat(evento.toString()).doesNotContain(CONTRASENA, INTENTO, "ana@correo.com", "Ana Gómez", "hash-real");
    }

    private EventoCritico eventoPublicado() {
        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(captor.capture());
        return captor.getValue();
    }
}
