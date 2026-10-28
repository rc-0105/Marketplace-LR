package co.edu.unbosque.marketplace.alojamientos.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.TipoAlojamientoNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;

class PublicacionServiceTest {

    private static final Long ANFITRION_ID = 7L;

    private final PublicacionRepository publicaciones = mock(PublicacionRepository.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final PublicacionService servicio = new PublicacionService(publicaciones, publicador);

    @BeforeEach
    void preparar() {
        // El repositorio devuelve la misma publicación que recibe.
        when(publicaciones.save(any(Publicacion.class))).thenAnswer(AdditionalAnswers.returnsFirstArg());
    }

    @Test
    @DisplayName("Con datos completos guarda la publicación en BORRADOR a nombre del anfitrión")
    void guardaBorradorDelAnfitrion() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", "Frente al mar", "Santa Marta",
                "Calle 1 # 2-3", "CASA", 4, new BigDecimal("250000"), List.of("Wifi", "Piscina"),
                "No se admiten fiestas", List.of("https://imagenes.com/1.jpg"));

        Publicacion publicacion = servicio.crearBorrador(ANFITRION_ID, solicitud);

        assertThat(publicacion.getAnfitrionId()).isEqualTo(ANFITRION_ID);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(publicacion.getTipo()).isEqualTo(TipoAlojamiento.CASA);
        assertThat(publicacion.getCapacidad()).isEqualTo(4);
        assertThat(publicacion.getServicios()).containsExactly("Wifi", "Piscina");
        assertThat(publicacion.getImagenes()).containsExactly("https://imagenes.com/1.jpg");
        assertThat(publicacion.camposFaltantesParaActivar()).isEmpty();
        verify(publicaciones).save(publicacion);
    }

    @Test
    @DisplayName("Con solo el título guarda el borrador y reporta los campos que faltan")
    void guardaBorradorSoloConTitulo() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", null, null, null, null, null,
                null, null, null, null);

        Publicacion publicacion = servicio.crearBorrador(ANFITRION_ID, solicitud);

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(publicacion.getServicios()).isEmpty();
        assertThat(publicacion.getImagenes()).isEmpty();
        assertThat(publicacion.camposFaltantesParaActivar()).containsExactly("descripcion", "ciudad",
                "direccion", "tipo", "capacidad", "precioNoche", "servicios", "reglas", "imagenes");
    }

    @Test
    @DisplayName("Quita los espacios sobrantes de los textos, los servicios y las imágenes")
    void recortaLosEspacios() {
        SolicitudBorrador solicitud = new SolicitudBorrador("  Casa en la playa  ", "  Frente al mar ", " Santa Marta ",
                null, " CASA ", null, null, List.of("  Wifi "), null, List.of(" https://imagenes.com/1.jpg "));

        Publicacion publicacion = servicio.crearBorrador(ANFITRION_ID, solicitud);

        assertThat(publicacion.getTitulo()).isEqualTo("Casa en la playa");
        assertThat(publicacion.getDescripcion()).isEqualTo("Frente al mar");
        assertThat(publicacion.getCiudad()).isEqualTo("Santa Marta");
        assertThat(publicacion.getTipo()).isEqualTo(TipoAlojamiento.CASA);
        assertThat(publicacion.getServicios()).containsExactly("Wifi");
        assertThat(publicacion.getImagenes()).containsExactly("https://imagenes.com/1.jpg");
    }

    @Test
    @DisplayName("Un texto opcional que solo tiene espacios se guarda como vacío")
    void guardaTextoEnBlancoComoNulo() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", "   ", null, null, "  ", null,
                null, null, null, null);

        Publicacion publicacion = servicio.crearBorrador(ANFITRION_ID, solicitud);

        assertThat(publicacion.getDescripcion()).isNull();
        assertThat(publicacion.getTipo()).isNull();
    }

    @Test
    @DisplayName("Los servicios repetidos se guardan una sola vez")
    void eliminaServiciosRepetidos() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", null, null, null, null, null,
                null, List.of("Wifi", "Piscina", " Wifi "), null, null);

        Publicacion publicacion = servicio.crearBorrador(ANFITRION_ID, solicitud);

        assertThat(publicacion.getServicios()).containsExactly("Wifi", "Piscina");
    }

    @Test
    @DisplayName("Si el tipo no existe lanza la excepción y no guarda nada")
    void rechazaTipoInexistente() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", null, null, null, "CASTILLO",
                null, null, null, null, null);

        assertThatThrownBy(() -> servicio.crearBorrador(ANFITRION_ID, solicitud))
                .isInstanceOf(TipoAlojamientoNoValidoException.class)
                .hasMessage("El tipo debe ser CASA, APARTAMENTO o HABITACION.");
        verify(publicaciones, never()).save(any(Publicacion.class));
        verify(publicador, never()).publicar(any(EventoCritico.class));
    }

    @Test
    @DisplayName("El tipo distingue mayúsculas: 'casa' en minúsculas no es válido")
    void rechazaTipoEnMinusculas() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", null, null, null, "casa",
                null, null, null, null, null);

        assertThatThrownBy(() -> servicio.crearBorrador(ANFITRION_ID, solicitud))
                .isInstanceOf(TipoAlojamientoNoValidoException.class);
    }

    @Test
    @DisplayName("Al crear el borrador deja un evento de auditoría con el anfitrión y la operación")
    void publicaEventoDeAuditoria() {
        SolicitudBorrador solicitud = new SolicitudBorrador("Casa en la playa", null, null, null, null, null,
                null, null, null, null);

        servicio.crearBorrador(ANFITRION_ID, solicitud);

        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(captor.capture());
        EventoCritico evento = captor.getValue();
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.accion()).isEqualTo(AccionCritica.PUBLICACION);
        assertThat(evento.entidad()).isEqualTo("Publicacion");
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.datos()).containsEntry("operacion", "crear_borrador");
    }
}
