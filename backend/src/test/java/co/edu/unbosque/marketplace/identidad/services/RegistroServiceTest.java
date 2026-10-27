package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.CorreoEnUsoException;
import co.edu.unbosque.marketplace.identidad.exceptions.RolNoPermitidoException;
import co.edu.unbosque.marketplace.identidad.model.EstadoCuenta;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistroServiceTest {

    private static final String CONTRASENA = "Segura#2026";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder codificador = mock(PasswordEncoder.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final RegistroService servicio = new RegistroService(usuarios, codificador, publicador);

    @BeforeEach
    void preparar() {
        when(codificador.encode(CONTRASENA)).thenReturn("hash-bcrypt");
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("Un visitante con datos válidos queda registrado como huésped con la cuenta activa")
    void registraHuespedConCuentaActiva() {
        Usuario usuario = servicio.registrar("Ana Gómez", "ana@correo.com", CONTRASENA, "HUESPED");

        assertThat(usuario.getNombre()).isEqualTo("Ana Gómez");
        assertThat(usuario.getRol()).isEqualTo(Rol.HUESPED);
        assertThat(usuario.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(usuarios).saveAndFlush(usuario);
    }

    @Test
    @DisplayName("Un visitante también puede registrarse como anfitrión")
    void registraAnfitrion() {
        Usuario usuario = servicio.registrar("Luis Pardo", "luis@correo.com", CONTRASENA, "ANFITRION");

        assertThat(usuario.getRol()).isEqualTo(Rol.ANFITRION);
    }

    @Test
    @DisplayName("La contraseña se guarda solo como hash, nunca en claro")
    void guardaLaContrasenaComoHash() {
        Usuario usuario = servicio.registrar("Ana Gómez", "ana@correo.com", CONTRASENA, "HUESPED");

        assertThat(usuario.getContrasenaHash()).isEqualTo("hash-bcrypt");
    }

    @Test
    @DisplayName("El correo se guarda sin espacios y en minúsculas")
    void normalizaElCorreo() {
        Usuario usuario = servicio.registrar("Ana Gómez", "  Ana@Correo.COM ", CONTRASENA, "HUESPED");

        assertThat(usuario.getCorreo()).isEqualTo("ana@correo.com");
    }

    @Test
    @DisplayName("Si el correo ya está registrado, informa que está en uso y no crea la cuenta")
    void rechazaCorreoEnUso() {
        when(usuarios.existsByCorreo("ana@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("Ana", "ANA@correo.com", CONTRASENA, "HUESPED"))
                .isInstanceOf(CorreoEnUsoException.class)
                .hasMessage("El correo ya está en uso.");
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Si dos registros con el mismo correo llegan a la vez, el segundo informa que está en uso")
    void traduceLaViolacionDeUnicidad() {
        when(usuarios.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("ux_usuario_correo"));

        assertThatThrownBy(() -> servicio.registrar("Ana", "ana@correo.com", CONTRASENA, "HUESPED"))
                .isInstanceOf(CorreoEnUsoException.class);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"ADMINISTRADOR", "SUPERADMIN", "huesped", ""})
    @DisplayName("Rechaza cualquier rol distinto de huésped o anfitrión")
    void rechazaRolNoPermitido(String rol) {
        assertThatThrownBy(() -> servicio.registrar("Ana", "ana@correo.com", CONTRASENA, rol))
                .isInstanceOf(RolNoPermitidoException.class)
                .hasMessage("El rol debe ser HUESPED o ANFITRION.");
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("El registro exitoso deja un evento de auditoría sin la contraseña")
    void auditaElRegistroSinContrasena() {
        servicio.registrar("Ana Gómez", "ana@correo.com", CONTRASENA, "HUESPED");

        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.entidad()).isEqualTo("Usuario");
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.datos())
                .containsEntry("operacion", "registro")
                .containsEntry("rol", "HUESPED");
        assertThat(evento.datos().values()).doesNotContain(CONTRASENA, "hash-bcrypt");
    }

    @Test
    @DisplayName("El intento con un correo en uso queda auditado como fallo")
    void auditaElRegistroFallido() {
        when(usuarios.existsByCorreo("ana@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("Ana", "ana@correo.com", CONTRASENA, "HUESPED"))
                .isInstanceOf(CorreoEnUsoException.class);

        EventoCritico evento = eventoPublicado();
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.FALLO);
        assertThat(evento.datos()).containsEntry("motivo", "correo_en_uso");
    }

    private EventoCritico eventoPublicado() {
        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(captor.capture());
        return captor.getValue();
    }
}
