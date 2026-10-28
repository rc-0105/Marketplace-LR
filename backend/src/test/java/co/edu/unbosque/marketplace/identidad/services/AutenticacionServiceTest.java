package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.UsuariosDePrueba;
import co.edu.unbosque.marketplace.identidad.exceptions.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaRestringidaException;
import co.edu.unbosque.marketplace.identidad.exceptions.CuentaYaActivaException;
import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AutenticacionServiceTest {

    private static final String MENSAJE = "Correo o contraseña incorrectos. Revísalos e intenta de nuevo.";
    private static final String CONTRASENA = "Segura#2026";
    private static final String HASH_FICTICIO = "hash-ficticio";
    private static final String MOTIVO = "Pagos en disputa";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder codificador = mock(PasswordEncoder.class);
    private final EmisorTokens emisor = mock(EmisorTokens.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final SesionService sesiones = mock(SesionService.class);
    private final Usuario ana = usuario();
    private AutenticacionService servicio;

    @BeforeEach
    void preparar() {
        when(codificador.encode(anyString())).thenReturn(HASH_FICTICIO);
        servicio = new AutenticacionService(usuarios, codificador, emisor, publicador, sesiones);
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(ana));
    }

    @Test
    @DisplayName("Con credenciales válidas devuelve la sesión y audita el login exitoso")
    void iniciaSesion() {
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", Instant.parse("2026-10-27T10:00:00Z"));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);
        when(emisor.emitir(ana)).thenReturn(token);

        SesionIniciada sesion = servicio.iniciarSesion("  Ana@Correo.COM ", CONTRASENA);

        assertThat(sesion.usuario()).isSameAs(ana);
        assertThat(sesion.token()).isSameAs(token);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry("operacion", "login");
        assertThat(evento.datos().values()).doesNotContain("jwt", "ana@correo.com", CONTRASENA);
    }

    @Test
    @DisplayName("Con la contraseña errónea lanza el error genérico y audita el fallo sin usuario")
    void rechazaContrasenaErronea() {
        when(codificador.matches("mala", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@correo.com", "mala"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        verify(codificador).matches("mala", "hash-real");
        verify(emisor, never()).emitir(ana);
        assertFalloAuditado();
    }

    @Test
    @DisplayName("Con un correo inexistente compara contra el hash ficticio y lanza el mismo error")
    void rechazaCorreoInexistente() {
        when(usuarios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciarSesion("nadie@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        verify(codificador).matches(CONTRASENA, HASH_FICTICIO);
        assertFalloAuditado();
    }

    @Test
    @DisplayName("El hash ficticio es un BCrypt válido y del mismo costo que los hashes reales")
    void hashFicticioEsBcryptValido() {
        PasswordEncoder real = spy(new BCryptPasswordEncoder());
        AutenticacionService conBcryptReal = new AutenticacionService(usuarios, real, emisor, publicador, sesiones);
        when(usuarios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conBcryptReal.iniciarSesion("nadie@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(real).matches(eq(CONTRASENA), hash.capture());
        String prefijoReal = real.encode(CONTRASENA).substring(0, 7);
        assertThat(hash.getValue()).startsWith(prefijoReal).hasSize(60);
    }

    @Test
    @DisplayName("Con la contraseña errónea una cuenta restringida da el 401 genérico y no revela el estado")
    void contrasenaErroneaEnCuentaRestringidaDa401() {
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(UsuariosDePrueba.restringida(7L, MOTIVO)));
        when(codificador.matches("mala", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@correo.com", "mala"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        verify(codificador, times(1)).matches(anyString(), anyString());
        assertFalloAuditado();
        assertThat(texto(eventoPublicado())).doesNotContain(MOTIVO);
    }

    @Test
    @DisplayName("Con la contraseña correcta una cuenta restringida da 403 con el motivo y audita al usuario")
    void rechazaCuentaRestringida() {
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(UsuariosDePrueba.restringida(7L, MOTIVO)));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@correo.com", CONTRASENA))
                .isInstanceOfSatisfying(CuentaRestringidaException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getPropiedades()).containsEntry("motivo", MOTIVO);
                });

        verify(codificador, times(1)).matches(anyString(), anyString());
        verify(emisor, never()).emitir(any());
        assertFalloPorEstado("cuenta_restringida");
    }

    @Test
    @DisplayName("Con la contraseña correcta una cuenta desactivada da 403 y audita al usuario")
    void rechazaCuentaDesactivada() {
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(UsuariosDePrueba.desactivada(7L)));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@correo.com", CONTRASENA))
                .isInstanceOf(CuentaDesactivadaException.class);

        verify(codificador, times(1)).matches(anyString(), anyString());
        verify(emisor, never()).emitir(any());
        assertFalloPorEstado("cuenta_desactivada");
    }

    @Test
    @DisplayName("Cerrar sesión invalida el jti y audita el logout sin token ni correo")
    void cierraSesion() {
        Instant expiraEn = Instant.parse("2026-10-27T10:00:00Z");

        servicio.cerrarSesion("jti-1", 7L, expiraEn);

        verify(sesiones).invalidar("jti-1", 7L, expiraEn);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry("operacion", "logout");
        assertThat(evento.datos().values()).doesNotContain("jti-1");
    }

    @Test
    @DisplayName("Reactivar con credenciales válidas activa la cuenta desactivada, devuelve la sesión y audita el éxito")
    void reactivaCuentaDesactivada() {
        Usuario desactivada = UsuariosDePrueba.desactivada(7L);
        TokenEmitido token = new TokenEmitido("jwt", "jti-1", Instant.parse("2026-10-27T10:00:00Z"));
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(desactivada));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);
        when(emisor.emitir(desactivada)).thenReturn(token);

        SesionIniciada sesion = servicio.reactivarCuenta(" Ana@Correo.COM ", CONTRASENA);

        assertThat(desactivada.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(sesion.usuario()).isSameAs(desactivada);
        assertThat(sesion.token()).isSameAs(token);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.entidadId()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry("operacion", "reactivar_cuenta");
        assertThat(texto(evento)).doesNotContain("jwt", "jti-1", "ana@correo.com", CONTRASENA);
    }

    @Test
    @DisplayName("Reactivar con un correo inexistente compara contra el hash ficticio y da el error genérico")
    void reactivarRechazaCorreoInexistente() {
        when(usuarios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reactivarCuenta("nadie@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        verify(codificador, times(1)).matches(anyString(), anyString());
        verify(codificador).matches(CONTRASENA, HASH_FICTICIO);
        assertFalloReactivarAnonimo();
    }

    @Test
    @DisplayName("Reactivar con la contraseña errónea da el error genérico aunque la cuenta no esté activa")
    void reactivarRechazaContrasenaErronea() {
        for (Usuario cuenta : List.of(UsuariosDePrueba.desactivada(7L), UsuariosDePrueba.restringida(7L, MOTIVO))) {
            when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(cuenta));
            when(codificador.matches("mala", "hash-real")).thenReturn(false);

            assertThatThrownBy(() -> servicio.reactivarCuenta("ana@correo.com", "mala"))
                    .isInstanceOf(CredencialesInvalidasException.class)
                    .hasMessage(MENSAJE);
            assertThat(cuenta.getEstado()).isNotEqualTo(EstadoCuenta.ACTIVA);
        }

        verify(emisor, never()).emitir(any());
        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador, times(2)).publicar(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(evento -> {
            assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
            assertThat(evento.usuario()).isEqualTo("anonimo");
            assertThat(evento.entidadId()).isNull();
            assertThat(evento.datos()).containsEntry("operacion", "reactivar_cuenta")
                    .containsEntry("motivo", "credenciales_invalidas");
        });
    }

    @Test
    @DisplayName("Una cuenta eliminada da el 401 genérico al iniciar sesión y se audita con su id")
    void rechazaCuentaEliminada() {
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(UsuariosDePrueba.eliminada(7L)));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        verify(codificador, times(1)).matches(anyString(), anyString());
        verify(emisor, never()).emitir(any());
        assertFalloPorEstado("cuenta_eliminada");
    }

    @Test
    @DisplayName("Reactivar una cuenta eliminada da el 401 genérico, no la cambia y audita el fallo")
    void reactivarRechazaCuentaEliminada() {
        Usuario eliminada = UsuariosDePrueba.eliminada(7L);
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(eliminada));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.reactivarCuenta("ana@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        assertThat(eliminada.getEstado()).isEqualTo(EstadoCuenta.ELIMINADA);
        verify(usuarios, never()).save(any());
        verify(emisor, never()).emitir(any());
        assertFalloReactivarPorEstado("cuenta_eliminada");
    }

    @Test
    @DisplayName("Reactivar una cuenta restringida da 403 con el motivo, no la cambia y audita el fallo")
    void reactivarNoLevantaRestriccion() {
        Usuario restringida = UsuariosDePrueba.restringida(7L, MOTIVO);
        when(usuarios.findByCorreo("ana@correo.com")).thenReturn(Optional.of(restringida));
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.reactivarCuenta("ana@correo.com", CONTRASENA))
                .isInstanceOfSatisfying(CuentaRestringidaException.class,
                        error -> assertThat(error.getPropiedades()).containsEntry("motivo", MOTIVO));

        assertThat(restringida.getEstado()).isEqualTo(EstadoCuenta.RESTRINGIDA);
        verify(emisor, never()).emitir(any());
        assertFalloReactivarPorEstado("cuenta_restringida");
    }

    @Test
    @DisplayName("Reactivar una cuenta ya activa da 409 y audita el fallo sin emitir token")
    void reactivarRechazaCuentaActiva() {
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.reactivarCuenta("ana@correo.com", CONTRASENA))
                .isInstanceOfSatisfying(CuentaYaActivaException.class, error -> {
                    assertThat(error.getEstado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getPropiedades()).containsEntry("codigo", "CUENTA_ACTIVA");
                });

        verify(emisor, never()).emitir(any());
        assertFalloReactivarPorEstado("cuenta_activa");
    }

    private void assertFalloReactivarAnonimo() {
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.usuario()).isEqualTo("anonimo");
        assertThat(evento.entidadId()).isNull();
        assertThat(evento.datos()).containsEntry("operacion", "reactivar_cuenta")
                .containsEntry("motivo", "credenciales_invalidas");
        assertThat(evento.datos().values()).doesNotContain("nadie@correo.com", CONTRASENA);
    }

    private void assertFalloReactivarPorEstado(String motivo) {
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.entidadId()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry("operacion", "reactivar_cuenta").containsEntry("motivo", motivo);
        assertThat(texto(evento)).doesNotContain(MOTIVO, "ana@correo.com", CONTRASENA, "hash-real");
    }

    private void assertFalloAuditado() {
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.usuario()).isEqualTo("anonimo");
        assertThat(evento.entidadId()).isNull();
        assertThat(evento.datos())
                .containsEntry("operacion", "login")
                .containsEntry("motivo", "credenciales_invalidas");
        assertThat(evento.datos().values()).doesNotContain("ana@correo.com", CONTRASENA, "mala");
    }

    private void assertFalloPorEstado(String motivo) {
        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.entidadId()).isEqualTo("7");
        assertThat(evento.datos()).containsEntry("operacion", "login").containsEntry("motivo", motivo);
        assertThat(texto(evento)).doesNotContain(MOTIVO, "ana@correo.com", CONTRASENA, "hash-real");
    }

    private static String texto(EventoCritico evento) {
        return evento.datos().values().toString() + evento.usuario() + evento.entidadId();
    }

    private EventoCritico eventoPublicado() {
        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(captor.capture());
        return captor.getValue();
    }

    private static Usuario usuario() {
        Usuario nuevo = Usuario.nuevo("Ana Gómez", "ana@correo.com", "hash-real", Rol.HUESPED);
        ReflectionTestUtils.setField(nuevo, "id", 7L);
        return nuevo;
    }
}
