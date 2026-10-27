package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AutenticacionServiceTest {

    private static final String MENSAJE = "Correo o contraseña incorrectos. Revísalos e intenta de nuevo.";
    private static final String CONTRASENA = "Segura#2026";
    private static final String HASH_FICTICIO = "hash-ficticio";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder codificador = mock(PasswordEncoder.class);
    private final EmisorTokens emisor = mock(EmisorTokens.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final Usuario ana = usuario();
    private AutenticacionService servicio;

    @BeforeEach
    void preparar() {
        when(codificador.encode(anyString())).thenReturn(HASH_FICTICIO);
        servicio = new AutenticacionService(usuarios, codificador, emisor, publicador);
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
        AutenticacionService conBcryptReal = new AutenticacionService(usuarios, real, emisor, publicador);
        when(usuarios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conBcryptReal.iniciarSesion("nadie@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(real).matches(eq(CONTRASENA), hash.capture());
        String prefijoReal = real.encode(CONTRASENA).substring(0, 7);
        assertThat(hash.getValue()).startsWith(prefijoReal).hasSize(60);
    }

    @Test
    @DisplayName("Una cuenta que no puede iniciar sesión recibe el mismo error aunque la contraseña coincida")
    void rechazaCuentaSinAcceso() {
        ReflectionTestUtils.setField(ana, "estado", null);
        when(codificador.matches(CONTRASENA, "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@correo.com", CONTRASENA))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(MENSAJE);

        assertFalloAuditado();
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
