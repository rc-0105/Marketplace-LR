package co.edu.unbosque.marketplace.resenas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.resenas.exceptions.ResenaNoEncontradaException;
import co.edu.unbosque.marketplace.resenas.exceptions.ResenaYaOcultaException;
import co.edu.unbosque.marketplace.resenas.model.EstadoResena;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import co.edu.unbosque.marketplace.resenas.repository.ResenaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Moderación de reseñas inapropiadas (SOF-82, HU-37, RF-55). */
class ModeracionResenasServiceTest {

    private static final Long RESENA_ID = 99L;
    private static final Long ADMINISTRADOR_ID = 1L;
    private static final Long PUBLICACION_ID = 30L;
    private static final String MOTIVO = "Lenguaje ofensivo contra el anfitrión";
    private static final Instant AHORA = Instant.parse("2026-12-20T15:00:00Z");

    private final ResenaRepository resenas = mock(ResenaRepository.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final Notificador notificador = mock(Notificador.class);
    private final ModeracionResenasService servicio = new ModeracionResenasService(resenas, publicador,
            notificador, Clock.fixed(AHORA, ZoneOffset.UTC));

    private Resena resenaVisible() {
        Resena resena = Resena.nueva(42L, 7L, PUBLICACION_ID, Instant.parse("2026-12-10T10:00:00Z"), 1,
                "Texto original de la reseña");
        ReflectionTestUtils.setField(resena, "id", RESENA_ID);
        when(resenas.findById(RESENA_ID)).thenReturn(Optional.of(resena));
        when(resenas.save(any(Resena.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        return resena;
    }

    @Test
    @DisplayName("AC1: al ocultar con motivo la reseña queda OCULTA con ese motivo")
    void laResenaQuedaOculta() {
        resenaVisible();

        Resena oculta = servicio.ocultar(RESENA_ID, ADMINISTRADOR_ID, MOTIVO);

        assertThat(oculta.getEstado()).isEqualTo(EstadoResena.OCULTA);
        assertThat(oculta.getMotivoOcultacion()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("AC2: la reseña oculta conserva su contenido, su calificación y su fecha originales")
    void laResenaSeConserva() {
        Resena resena = resenaVisible();

        Resena oculta = servicio.ocultar(RESENA_ID, ADMINISTRADOR_ID, MOTIVO);

        assertThat(oculta.getContenido()).isEqualTo("Texto original de la reseña");
        assertThat(oculta.getCalificacion()).isEqualTo(1);
        assertThat(oculta.getFecha()).isEqualTo(Instant.parse("2026-12-10T10:00:00Z"));
        verify(resenas, never()).delete(any(Resena.class));
        verify(resenas, never()).deleteById(any(Long.class));
        assertThat(oculta).isSameAs(resena);
    }

    @Test
    @DisplayName("AC3: queda un evento con el administrador, la reseña, la fecha y el motivo")
    void quedaAuditada() {
        resenaVisible();

        servicio.ocultar(RESENA_ID, ADMINISTRADOR_ID, MOTIVO);

        verify(publicador).publicar(argThat((EventoCritico evento) ->
                evento.usuario().equals("1")
                        && evento.accion() == AccionCritica.ACCION_ADMINISTRATIVA
                        && evento.entidad().equals("Resena")
                        && evento.entidadId().equals("99")
                        && evento.resultado() == ResultadoEvento.EXITO
                        && evento.fecha().equals(AHORA)
                        && MOTIVO.equals(evento.datos().get("motivo"))));
    }

    @Test
    @DisplayName("El huésped autor recibe una notificación con el motivo por el que se ocultó su reseña")
    void notificaAlAutor() {
        resenaVisible();

        servicio.ocultar(RESENA_ID, ADMINISTRADOR_ID, MOTIVO);

        verify(notificador).notificar(argThat((NuevaNotificacion notificacion) ->
                notificacion.destinatarioId().equals(7L)
                        && notificacion.tipo() == TipoNotificacion.RESENA_OCULTA
                        && notificacion.mensaje().contains(MOTIVO)
                        && notificacion.entidad().equals("Resena")
                        && notificacion.entidadId().equals(RESENA_ID)));
    }

    @Test
    @DisplayName("Una reseña que no existe responde 404 y no audita")
    void resenaInexistente() {
        when(resenas.findById(RESENA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ocultar(RESENA_ID, ADMINISTRADOR_ID, MOTIVO))
                .isInstanceOf(ResenaNoEncontradaException.class);
        verify(publicador, never()).publicar(any(EventoCritico.class));
        verify(notificador, never()).notificar(any(NuevaNotificacion.class));
    }

    @Test
    @DisplayName("Una reseña ya oculta responde 409, conserva su primer motivo y no audita otra vez")
    void yaOculta() {
        Resena resena = resenaVisible();
        resena.ocultar("Primer motivo");

        assertThatThrownBy(() -> servicio.ocultar(RESENA_ID, ADMINISTRADOR_ID, MOTIVO))
                .isInstanceOf(ResenaYaOcultaException.class);
        assertThat(resena.getMotivoOcultacion()).isEqualTo("Primer motivo");
        verify(publicador, never()).publicar(any(EventoCritico.class));
    }
}
