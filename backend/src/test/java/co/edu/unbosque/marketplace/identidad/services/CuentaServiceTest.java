package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final String DESACTIVAR = "desactivar_cuenta";
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
        assertThat(evento.datos()).containsEntry("operacion", "restringir_cuenta");
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
        assertThat(evento.datos()).containsEntry("operacion", "levantar_restriccion");
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
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, DESACTIVAR));
        sinDatosPersonales(evento);
    }

    @Test
    @DisplayName("Una contraseña incorrecta da 403, audita el fallo y no consulta reservas ni guarda")
    void rechazaContrasenaIncorrecta() {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));

        assertThatThrownBy(() -> servicio.desactivar(AFECTADO, INTENTO))
                .isInstanceOf(ConfirmacionInvalidaException.class);

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(usuarios, never()).save(ana);
        verifyNoInteractions(reservas);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, DESACTIVAR),
                Map.entry(MOTIVO_FALLO, "credenciales_invalidas"));
        sinDatosPersonales(evento);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"RESTRINGIDA", "DESACTIVADA"})
    @DisplayName("Una cuenta que no está activa da 409, audita el fallo y no toca el puerto de reservas")
    void rechazaCuentaQueNoEstaActiva(EstadoCuenta estado) {
        Usuario ana = dueno(UsuariosDePrueba.conEstado(AFECTADO, estado,
                estado == EstadoCuenta.RESTRINGIDA ? MOTIVO : null));
        contrasenaCorrecta(ana);

        assertThatThrownBy(() -> servicio.desactivar(AFECTADO, CONTRASENA))
                .isInstanceOf(OperacionCuentaNoPermitidaException.class)
                .hasMessage("Solo se puede desactivar una cuenta activa.");

        assertThat(ana.getEstado()).isEqualTo(estado);
        verify(usuarios, never()).save(ana);
        verifyNoInteractions(reservas);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, DESACTIVAR),
                Map.entry(MOTIVO_FALLO, "estado_no_permitido"));
        sinDatosPersonales(evento);
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 3L})
    @DisplayName("Con reservas pendientes da 409 con el conteo, audita el fallo con el número y no guarda")
    void rechazaCuentaConReservasFuturas(long pendientes) {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));
        contrasenaCorrecta(ana);
        when(reservas.contarConfirmadasFuturas(AFECTADO)).thenReturn(pendientes);

        assertThatThrownBy(() -> servicio.desactivar(AFECTADO, CONTRASENA))
                .isInstanceOfSatisfying(ReservasFuturasException.class, error -> {
                    assertThat(error.getPropiedades()).containsEntry(BLOQUEANTES, pendientes);
                    assertThat(error.getMessage()).contains("(" + pendientes + ")");
                });

        assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(usuarios, never()).save(ana);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.datos()).containsOnly(Map.entry(OPERACION, DESACTIVAR),
                Map.entry(MOTIVO_FALLO, "reservas_futuras"), Map.entry(BLOQUEANTES, pendientes));
        sinDatosPersonales(evento);
    }

    @Test
    @DisplayName("El orden es contraseña, reservas y recién entonces se guarda")
    void verificaEnOrden() {
        Usuario ana = dueno(UsuariosDePrueba.activa(AFECTADO));
        contrasenaCorrecta(ana);
        when(reservas.contarConfirmadasFuturas(AFECTADO)).thenReturn(0L);

        servicio.desactivar(AFECTADO, CONTRASENA);

        InOrder orden = inOrder(codificador, reservas, usuarios);
        orden.verify(codificador).matches(CONTRASENA, ana.getContrasenaHash());
        orden.verify(reservas).contarConfirmadasFuturas(AFECTADO);
        orden.verify(usuarios).save(ana);
    }

    @Test
    @DisplayName("Desactivar un usuario inexistente da 404, sin evento ni consulta de reservas")
    void desactivarUsuarioInexistente() {
        when(usuarios.findById(AFECTADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.desactivar(AFECTADO, CONTRASENA))
                .isInstanceOf(UsuarioNoEncontradoException.class);

        verifyNoInteractions(publicador, reservas);
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
