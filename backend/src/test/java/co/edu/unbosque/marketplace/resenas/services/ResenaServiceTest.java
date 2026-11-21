package co.edu.unbosque.marketplace.resenas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservaParaResena;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaParaResena;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.resenas.exceptions.ReservaNoEncontradaParaResenaException;
import co.edu.unbosque.marketplace.resenas.exceptions.ReservaNoResenableException;
import co.edu.unbosque.marketplace.resenas.exceptions.ResenaYaExisteException;
import co.edu.unbosque.marketplace.resenas.model.EstadoResena;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import co.edu.unbosque.marketplace.resenas.repository.ResenaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Creación de la reseña y la calificación tras la estancia (SOF-80, HU-35, RF-53). */
class ResenaServiceTest {

    private static final Long RESERVA_ID = 42L;
    private static final Long HUESPED_ID = 7L;
    private static final Long OTRO_HUESPED_ID = 55L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Instant AHORA = Instant.parse("2026-12-20T15:00:00Z");

    private final ResenaRepository resenas = mock(ResenaRepository.class);
    private final ConsultaReservaParaResena reservas = mock(ConsultaReservaParaResena.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final ResenaService servicio = new ResenaService(resenas, reservas, publicador,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private void reservaEn(String estado) {
        when(reservas.consultar(RESERVA_ID))
                .thenReturn(Optional.of(new ReservaParaResena(RESERVA_ID, HUESPED_ID, PUBLICACION_ID, estado)));
    }

    // La base asigna el id al guardar; el mock lo imita para que la auditoría pueda citarlo.
    private void guardaDevolviendoId() {
        when(resenas.saveAndFlush(any(Resena.class))).thenAnswer(invocacion -> {
            Resena guardada = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 99L);
            return guardada;
        });
    }

    @Test
    @DisplayName("AC1: con la reserva COMPLETADA la reseña queda con huésped, reserva, alojamiento, fecha y visible")
    void laResenaQuedaAsociada() {
        reservaEn("COMPLETADA");
        guardaDevolviendoId();

        Resena resena = servicio.crear(RESERVA_ID, HUESPED_ID, 4, "Muy cómodo y limpio");

        assertThat(resena.getReservaId()).isEqualTo(RESERVA_ID);
        assertThat(resena.getHuespedId()).isEqualTo(HUESPED_ID);
        assertThat(resena.getPublicacionId()).isEqualTo(PUBLICACION_ID);
        assertThat(resena.getFecha()).isEqualTo(AHORA);
        assertThat(resena.getCalificacion()).isEqualTo(4);
        assertThat(resena.getContenido()).isEqualTo("Muy cómodo y limpio");
        assertThat(resena.getEstado()).isEqualTo(EstadoResena.VISIBLE);
    }

    @Test
    @DisplayName("AC1: la reseña creada queda en la auditoría con la acción RESENA y el huésped")
    void laResenaQuedaAuditada() {
        reservaEn("COMPLETADA");
        guardaDevolviendoId();

        servicio.crear(RESERVA_ID, HUESPED_ID, 5, "Excelente");

        verify(publicador).publicar(argThat(evento -> evento.accion() == AccionCritica.RESENA
                && evento.resultado() == ResultadoEvento.EXITO
                && evento.usuario().equals(String.valueOf(HUESPED_ID))
                && evento.entidadId().equals(String.valueOf(RESERVA_ID))));
    }

    @Test
    @DisplayName("AC2: con la reserva CONFIRMADA y la estancia sin terminar el sistema impide reseñar")
    void noSeResenaUnaReservaConfirmada() {
        reservaEn("CONFIRMADA");

        assertThatThrownBy(() -> servicio.crear(RESERVA_ID, HUESPED_ID, 5, "Todavía no fui"))
                .isInstanceOf(ReservaNoResenableException.class)
                .hasMessageContaining("aún no ha terminado");
        verify(resenas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC2: una reserva CANCELADA, VENCIDA o PENDIENTE tampoco se reseña")
    void noSeResenaUnaReservaQueNoSeCompleto() {
        for (String estado : new String[] {"CANCELADA", "VENCIDA", "PENDIENTE"}) {
            reservaEn(estado);

            assertThatThrownBy(() -> servicio.crear(RESERVA_ID, HUESPED_ID, 3, "Comentario"))
                    .as(estado)
                    .isInstanceOf(ReservaNoResenableException.class);
        }
        verify(resenas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC3: reseñar por segunda vez la misma reserva se rechaza e indica que ya existe una reseña")
    void noSeResenaDosVeces() {
        reservaEn("COMPLETADA");
        when(resenas.existsByReservaId(RESERVA_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(RESERVA_ID, HUESPED_ID, 5, "Otra vez"))
                .isInstanceOf(ResenaYaExisteException.class)
                .hasMessageContaining("Ya existe una reseña");
        verify(resenas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC3: si dos solicitudes llegan a la vez, la que pierde la restricción única recibe el mismo 409")
    void laCarreraTambienSeRechaza() {
        reservaEn("COMPLETADA");
        when(resenas.existsByReservaId(RESERVA_ID)).thenReturn(false, true);
        when(resenas.saveAndFlush(any(Resena.class))).thenThrow(new DataIntegrityViolationException("ux_resena"));

        assertThatThrownBy(() -> servicio.crear(RESERVA_ID, HUESPED_ID, 5, "Carrera"))
                .isInstanceOf(ResenaYaExisteException.class);
        verify(publicador, never()).publicar(any());
    }

    @Test
    @DisplayName("AC4: reseñar la reserva de otro huésped responde 403, no guarda nada y el intento queda auditado")
    void unaReservaAjenaSeNiega() {
        reservaEn("COMPLETADA");

        assertThatThrownBy(() -> servicio.crear(RESERVA_ID, OTRO_HUESPED_ID, 5, "No me hospedé aquí"))
                .isInstanceOf(AccesoDenegadoException.class);

        verify(resenas, never()).saveAndFlush(any());
        verify(publicador).publicar(argThat(evento -> evento.accion() == AccionCritica.RESENA
                && evento.resultado() == ResultadoEvento.FALLO
                && evento.usuario().equals(String.valueOf(OTRO_HUESPED_ID))));
    }

    @Test
    @DisplayName("una reserva que no existe responde 404")
    void unaReservaInexistenteNoSeEncuentra() {
        when(reservas.consultar(RESERVA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(RESERVA_ID, HUESPED_ID, 5, "Comentario"))
                .isInstanceOf(ReservaNoEncontradaParaResenaException.class);
    }
}
