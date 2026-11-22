package co.edu.unbosque.marketplace.reservas.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.identidad.services.DirectorioUsuariosService;
import co.edu.unbosque.marketplace.reservas.exceptions.ReservaNoEncontradaException;
import co.edu.unbosque.marketplace.reservas.exceptions.ResolucionInvalidaException;
import co.edu.unbosque.marketplace.reservas.exceptions.SolicitudNoRespondibleException;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Aprobación, rechazo y vencimiento de solicitudes de reserva por el anfitrión (SOF-79, HU-34, RF-52). */
class AprobacionReservasServiceTest {

    private static final Long RESERVA_ID = 42L;
    private static final Long HUESPED_ID = 7L;
    private static final Long ANFITRION_ID = 9L;
    private static final Long OTRO_ID = 55L;
    private static final Long PUBLICACION_ID = 30L;
    private static final Instant AHORA = Instant.parse("2026-12-01T15:00:00Z");

    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PublicacionService publicaciones = mock(PublicacionService.class);
    private final DirectorioUsuariosService usuarios = mock(DirectorioUsuariosService.class);
    private final RegistroReservas registro = mock(RegistroReservas.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final NotificacionesReserva notificaciones = mock(NotificacionesReserva.class);
    private final AprobacionReservasService servicio = new AprobacionReservasService(reservas, publicaciones,
            usuarios, registro, publicador, notificaciones, Clock.fixed(AHORA, ZoneOffset.UTC), 24);

    private void preparar(EstadoReserva estado) {
        when(reservas.findById(RESERVA_ID)).thenReturn(Optional.of(solicitud(estado)));
        when(publicaciones.consultarAcceso(PUBLICACION_ID))
                .thenReturn(Optional.of(new AccesoPublicacion(ANFITRION_ID, true)));
    }

    @Test
    @DisplayName("Aprobar: pasa a PENDIENTE con apartado de pago horas-pago-aprobada, audita y avisa al huésped")
    void apruebaLaSolicitud() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        when(registro.aprobarSolicitud(RESERVA_ID, AHORA.plusSeconds(24 * 3600))).thenReturn(true);

        ResolucionSolicitud resultado = servicio.aprobar(RESERVA_ID, ANFITRION_ID);

        assertThat(resultado.reservaId()).isEqualTo(RESERVA_ID);
        assertThat(resultado.estado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(resultado.venceEn()).isEqualTo(AHORA.plusSeconds(24 * 3600));
        verify(notificaciones).notificarHuesped(any(Reserva.class), eq(TipoNotificacion.SOLICITUD_APROBADA),
                isNull());
        assertThat(auditado().resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(auditado().datos()).containsEntry("operacion", "aprobar_solicitud");
    }

    @Test
    @DisplayName("Aprobar: con horas-pago-aprobada distinto del defecto el apartado dura ese plazo")
    void apruebaConPlazoConfigurado() {
        AprobacionReservasService de6 = new AprobacionReservasService(reservas, publicaciones, usuarios, registro,
                publicador, notificaciones, Clock.fixed(AHORA, ZoneOffset.UTC), 6);
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        when(registro.aprobarSolicitud(RESERVA_ID, AHORA.plusSeconds(6 * 3600))).thenReturn(true);

        ResolucionSolicitud resultado = de6.aprobar(RESERVA_ID, ANFITRION_ID);

        assertThat(resultado.venceEn()).isEqualTo(AHORA.plusSeconds(6 * 3600));
    }

    @Test
    @DisplayName("Aprobar: quien no es el anfitrión de la publicación recibe 403 y el intento queda auditado")
    void aprobarDeOtroAnfitrionEs403() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);

        assertThatThrownBy(() -> servicio.aprobar(RESERVA_ID, OTRO_ID)).isInstanceOf(AccesoDenegadoException.class);

        verify(registro, never()).aprobarSolicitud(anyLong(), any());
        assertThat(auditado().resultado()).isEqualTo(ResultadoEvento.FALLO);
    }

    @Test
    @DisplayName("Aprobar: una reserva inexistente responde 404")
    void aprobarInexistenteEs404() {
        when(reservas.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(999L, ANFITRION_ID)).isInstanceOf(ReservaNoEncontradaException.class);
    }

    @Test
    @DisplayName("Aprobar: una reserva que ya no es PENDIENTE_APROBACION responde 409 sin tocar nada")
    void aprobarYaRespondidaEs409() {
        preparar(EstadoReserva.RECHAZADA);

        assertThatThrownBy(() -> servicio.aprobar(RESERVA_ID, ANFITRION_ID))
                .isInstanceOf(SolicitudNoRespondibleException.class);

        verify(registro, never()).aprobarSolicitud(anyLong(), any());
        verify(notificaciones, never()).notificarHuesped(any(), any(), any());
    }

    @Test
    @DisplayName("Aprobar: con la publicación bloqueada (SOF-83) responde 409")
    void aprobarConPublicacionBloqueadaEs409() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        when(publicaciones.estaBloqueada(PUBLICACION_ID)).thenReturn(true);

        assertThatThrownBy(() -> servicio.aprobar(RESERVA_ID, ANFITRION_ID))
                .isInstanceOf(SolicitudNoRespondibleException.class).hasMessageContaining("bloque");

        verify(registro, never()).aprobarSolicitud(anyLong(), any());
    }

    @Test
    @DisplayName("Aprobar: si la actualización condicional afecta 0 filas (venció o perdió la carrera) responde 409 sin avisar")
    void aprobarPierdeLaCarrera() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        when(registro.aprobarSolicitud(anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> servicio.aprobar(RESERVA_ID, ANFITRION_ID))
                .isInstanceOf(SolicitudNoRespondibleException.class);

        verify(notificaciones, never()).notificarHuesped(any(), any(), any());
        verify(publicador, never()).publicar(org.mockito.ArgumentMatchers.argThat(e -> e.resultado() == ResultadoEvento.EXITO));
    }

    @Test
    @DisplayName("Rechazar: guarda el motivo recortado, avisa al huésped con el motivo y audita")
    void rechazaLaSolicitud() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        when(registro.rechazarSolicitud(RESERVA_ID, "No disponible")).thenReturn(true);

        ResolucionSolicitud resultado = servicio.rechazar(RESERVA_ID, ANFITRION_ID, "  No disponible  ");

        assertThat(resultado.estado()).isEqualTo(EstadoReserva.RECHAZADA);
        assertThat(resultado.motivo()).isEqualTo("No disponible");
        verify(notificaciones).notificarHuesped(any(Reserva.class), eq(TipoNotificacion.SOLICITUD_RECHAZADA),
                eq("No disponible"));
        assertThat(auditado().datos()).containsEntry("operacion", "rechazar_solicitud")
                .containsEntry("motivo", "No disponible");
    }

    @Test
    @DisplayName("Rechazar: el motivo es obligatorio (vacío o solo espacios) y máximo 500 caracteres")
    void rechazarValidaElMotivo() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);

        assertThatThrownBy(() -> servicio.rechazar(RESERVA_ID, ANFITRION_ID, "   "))
                .isInstanceOf(ResolucionInvalidaException.class);
        assertThatThrownBy(() -> servicio.rechazar(RESERVA_ID, ANFITRION_ID, null))
                .isInstanceOf(ResolucionInvalidaException.class);
        assertThatThrownBy(() -> servicio.rechazar(RESERVA_ID, ANFITRION_ID, "x".repeat(501)))
                .isInstanceOf(ResolucionInvalidaException.class);

        verify(registro, never()).rechazarSolicitud(anyLong(), any());
    }

    @Test
    @DisplayName("Rechazar: de otro anfitrión es 403; perdiendo la carrera es 409 sin avisar")
    void rechazarAutorizaYPierdeLaCarrera() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        assertThatThrownBy(() -> servicio.rechazar(RESERVA_ID, OTRO_ID, "No")).isInstanceOf(AccesoDenegadoException.class);

        when(registro.rechazarSolicitud(anyLong(), any())).thenReturn(false);
        assertThatThrownBy(() -> servicio.rechazar(RESERVA_ID, ANFITRION_ID, "No"))
                .isInstanceOf(SolicitudNoRespondibleException.class);
        verify(notificaciones, never()).notificarHuesped(any(), any(), any());
    }

    @Test
    @DisplayName("Aprobar: una notificación que falla no deshace la aprobación")
    void laNotificacionNoRompeLaAprobacion() {
        preparar(EstadoReserva.PENDIENTE_APROBACION);
        when(registro.aprobarSolicitud(anyLong(), any())).thenReturn(true);
        doThrow(new IllegalStateException("fallo")).when(notificaciones).notificarHuesped(any(), any(), any());

        assertThat(servicio.aprobar(RESERVA_ID, ANFITRION_ID).estado()).isEqualTo(EstadoReserva.PENDIENTE);
    }

    @Test
    @DisplayName("Listar: sin publicaciones responde vacío sin consultar reservas")
    void listarSinPublicaciones() {
        when(publicaciones.listarDelAnfitrion(ANFITRION_ID)).thenReturn(List.of());

        assertThat(servicio.listarSolicitudes(ANFITRION_ID)).isEmpty();

        verify(reservas, never()).findSolicitudesVigentes(any(), any());
    }

    private ArgumentCaptor<EventoCritico> capturador() {
        return ArgumentCaptor.forClass(EventoCritico.class);
    }

    private EventoCritico auditado() {
        ArgumentCaptor<EventoCritico> evento = capturador();
        verify(publicador).publicar(evento.capture());
        return evento.getValue();
    }

    private static Reserva solicitud(EstadoReserva estado) {
        Reserva reserva = Reserva.nuevaSolicitud(PUBLICACION_ID, HUESPED_ID, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 14), 4, new BigDecimal("400000"), new BigDecimal("30000"),
                new BigDecimal("40000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("470000"), Set.of(),
                AHORA.plusSeconds(3600));
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }
}
