package co.edu.unbosque.marketplace.identidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.identidad.exceptions.CodigoRecuperacionInvalidoException;
import co.edu.unbosque.marketplace.identidad.model.Rol;
import co.edu.unbosque.marketplace.identidad.model.TokenRecuperacion;
import co.edu.unbosque.marketplace.identidad.model.Usuario;
import co.edu.unbosque.marketplace.identidad.repository.TokenRecuperacionRepository;
import co.edu.unbosque.marketplace.identidad.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class RecuperacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T10:00:00Z");
    private static final String CORREO = "ana@correo.com";
    private static final String NUEVA = "Nueva#2026";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TokenRecuperacionRepository tokens = mock(TokenRecuperacionRepository.class);
    private final PasswordEncoder codificador = mock(PasswordEncoder.class);
    private final EnvioCodigoRecuperacion envio = mock(EnvioCodigoRecuperacion.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final Usuario ana = usuario();
    private RecuperacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new RecuperacionService(usuarios, tokens, codificador, envio, publicador,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(ana));
        when(codificador.encode(NUEVA)).thenReturn("hash-nuevo");
    }

    @Test
    @DisplayName("Al solicitar con un correo registrado envía un código de 6 dígitos y guarda solo su hash")
    void enviaCodigoAlCorreoRegistrado() {
        String codigo = solicitarYCapturar();

        assertThat(codigo).matches("\\d{6}");
        TokenRecuperacion guardado = tokenGuardado();
        assertThat(guardado.getUsuarioId()).isEqualTo(7L);
        assertThat(guardado.getCodigoHash()).hasSize(64).isNotEqualTo(codigo);
        assertThat(guardado.getVenceEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
        verify(envio).enviar(CORREO, codigo, AHORA.plus(Duration.ofMinutes(15)));
        EventoCritico evento = eventoPublicado();
        assertThat(evento.accion()).isEqualTo(AccionCritica.AUTENTICACION);
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.datos()).containsEntry("operacion", "solicitud_recuperacion");
        assertThat(evento.datos().values()).doesNotContain(codigo, CORREO);
    }

    @Test
    @DisplayName("Pedir un código nuevo anula los códigos pendientes anteriores")
    void anulaCodigosPendientes() {
        TokenRecuperacion anterior = TokenRecuperacion.emitir(7L, "hash", AHORA.plusSeconds(60));
        when(tokens.findPendientesParaActualizar(7L)).thenReturn(List.of(anterior));

        servicio.solicitar(CORREO);

        assertThat(anterior.estaVigente(AHORA)).isFalse();
    }

    @Test
    @DisplayName("Con un correo no registrado no envía nada, no falla y audita el intento")
    void ignoraCorreoNoRegistrado() {
        when(usuarios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());

        servicio.solicitar(" Nadie@Correo.com ");

        verify(envio, never()).enviar(anyString(), anyString(), any());
        verify(tokens, never()).save(any());
        assertThat(eventoPublicado().resultado()).isEqualTo(ResultadoEvento.FALLO);
    }

    @Test
    @DisplayName("Un código vigente cambia la contraseña y queda marcado como usado")
    void cambiaContrasenaConCodigoVigente() {
        String codigo = solicitarYCapturar();
        TokenRecuperacion token = tokenGuardado();
        when(tokens.findPendientesParaActualizar(7L)).thenReturn(List.of(token));

        servicio.confirmar(CORREO, codigo, NUEVA);

        assertThat(ana.getContrasenaHash()).isEqualTo("hash-nuevo");
        assertThat(token.estaVigente(AHORA)).isFalse();
        assertThat(eventoPublicado().datos()).containsEntry("operacion", "confirmacion_recuperacion");
    }

    @Test
    @DisplayName("Un código vencido se rechaza y la contraseña anterior sigue vigente")
    void rechazaCodigoVencido() {
        String codigo = solicitarYCapturar();
        TokenRecuperacion token = tokenGuardado();
        ReflectionTestUtils.setField(token, "venceEn", AHORA.minusSeconds(1));
        when(tokens.findPendientesParaActualizar(7L)).thenReturn(List.of(token));

        assertThatThrownBy(() -> servicio.confirmar(CORREO, codigo, NUEVA))
                .isInstanceOf(CodigoRecuperacionInvalidoException.class);
        assertThat(ana.getContrasenaHash()).isEqualTo("hash-real");
    }

    @Test
    @DisplayName("Un código ya usado se rechaza")
    void rechazaCodigoUsado() {
        String codigo = solicitarYCapturar();
        TokenRecuperacion token = tokenGuardado();
        token.marcarUsado(AHORA);
        when(tokens.findPendientesParaActualizar(7L)).thenReturn(List.of(token));

        assertThatThrownBy(() -> servicio.confirmar(CORREO, codigo, NUEVA))
                .isInstanceOf(CodigoRecuperacionInvalidoException.class);
        assertThat(ana.getContrasenaHash()).isEqualTo("hash-real");
    }

    @Test
    @DisplayName("Tras 5 códigos erróneos el código correcto también se rechaza")
    void anulaCodigoTrasCincoIntentosFallidos() {
        String codigo = solicitarYCapturar();
        TokenRecuperacion token = tokenGuardado();
        when(tokens.findPendientesParaActualizar(7L)).thenReturn(List.of(token));
        String erroneo = codigo.equals("000000") ? "111111" : "000000";

        for (int intento = 0; intento < 5; intento++) {
            assertThatThrownBy(() -> servicio.confirmar(CORREO, erroneo, NUEVA))
                    .isInstanceOf(CodigoRecuperacionInvalidoException.class);
        }

        assertThatThrownBy(() -> servicio.confirmar(CORREO, codigo, NUEVA))
                .isInstanceOf(CodigoRecuperacionInvalidoException.class);
        assertThat(ana.getContrasenaHash()).isEqualTo("hash-real");
    }

    @Test
    @DisplayName("Con un correo no registrado la confirmación da el mismo error que un código inválido")
    void rechazaConfirmacionDeCorreoNoRegistrado() {
        when(usuarios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.confirmar("nadie@correo.com", "123456", NUEVA))
                .isInstanceOf(CodigoRecuperacionInvalidoException.class)
                .hasMessage("El código no es válido o ya venció. Solicita uno nuevo.");
        verify(codificador, never()).encode(anyString());
    }

    private String solicitarYCapturar() {
        servicio.solicitar(CORREO);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(envio).enviar(eq(CORREO), codigo.capture(), any());
        return codigo.getValue();
    }

    private TokenRecuperacion tokenGuardado() {
        ArgumentCaptor<TokenRecuperacion> token = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokens).save(token.capture());
        return token.getValue();
    }

    private EventoCritico eventoPublicado() {
        ArgumentCaptor<EventoCritico> evento = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador, atLeastOnce()).publicar(evento.capture());
        return evento.getValue();
    }

    private static Usuario usuario() {
        Usuario usuario = Usuario.nuevo("Ana Gómez", CORREO, "hash-real", Rol.HUESPED);
        ReflectionTestUtils.setField(usuario, "id", 7L);
        return usuario;
    }
}
