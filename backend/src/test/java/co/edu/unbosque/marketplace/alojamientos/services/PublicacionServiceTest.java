package co.edu.unbosque.marketplace.alojamientos.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.TipoAlojamientoNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;

class PublicacionServiceTest {

    private static final Long ANFITRION_ID = 7L;
    private static final Long OTRO_ANFITRION_ID = 8L;
    private static final Long ADMINISTRADOR_ID = 1L;
    private static final Long PUBLICACION_ID = 30L;

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

    @Test
    @DisplayName("El dueño edita su publicación: reemplaza los datos y registra quién editó y cuándo")
    void duenoEditaSuPublicacion() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));
        SolicitudBorrador cambios = new SolicitudBorrador(" Casa renovada ", "Con vista al mar", null, null,
                "APARTAMENTO", 6, new BigDecimal("300000"), List.of("Wifi"), null, List.of());

        Publicacion editada = servicio.editar(PUBLICACION_ID, ANFITRION_ID, false, cambios);

        assertThat(editada.getTitulo()).isEqualTo("Casa renovada");
        assertThat(editada.getDescripcion()).isEqualTo("Con vista al mar");
        assertThat(editada.getCiudad()).isNull();
        assertThat(editada.getTipo()).isEqualTo(TipoAlojamiento.APARTAMENTO);
        assertThat(editada.getCapacidad()).isEqualTo(6);
        assertThat(editada.getPrecioNoche()).isEqualByComparingTo("300000");
        assertThat(editada.getServicios()).containsExactly("Wifi");
        assertThat(editada.getImagenes()).isEmpty();
        assertThat(editada.getAnfitrionId()).isEqualTo(ANFITRION_ID);
        assertThat(editada.getEditadoPor()).isEqualTo(ANFITRION_ID);
        assertThat(editada.getEditadoEn()).isNotNull();
        verify(publicaciones).save(existente);
    }

    @Test
    @DisplayName("Al editar su publicación, el dueño deja un evento de publicación con la operación editar")
    void duenoEditaYPublicaEvento() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(publicacionDe(ANFITRION_ID)));

        servicio.editar(PUBLICACION_ID, ANFITRION_ID, false, soloTitulo("Casa renovada"));

        EventoCritico evento = eventoPublicado();
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.accion()).isEqualTo(AccionCritica.PUBLICACION);
        assertThat(evento.entidad()).isEqualTo("Publicacion");
        assertThat(evento.entidadId()).isEqualTo(String.valueOf(PUBLICACION_ID));
        assertThat(evento.resultado()).isEqualTo(ResultadoEvento.EXITO);
        assertThat(evento.datos()).containsEntry("operacion", "editar");
    }

    @Test
    @DisplayName("Otro anfitrión no puede editar la publicación: lanza 403 y no guarda ni audita nada")
    void otroAnfitrionNoPuedeEditar() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.editar(PUBLICACION_ID, OTRO_ANFITRION_ID, false,
                soloTitulo("Casa ajena")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(existente.getTitulo()).isEqualTo("Casa en la playa");
        verify(publicaciones, never()).save(any(Publicacion.class));
        verify(publicador, never()).publicar(any(EventoCritico.class));
    }

    @Test
    @DisplayName("El administrador edita cualquier publicación y queda registrada como acción administrativa")
    void administradorEditaCualquierPublicacion() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(publicacionDe(ANFITRION_ID)));

        Publicacion editada = servicio.editar(PUBLICACION_ID, ADMINISTRADOR_ID, true, soloTitulo("Título corregido"));

        assertThat(editada.getTitulo()).isEqualTo("Título corregido");
        assertThat(editada.getAnfitrionId()).isEqualTo(ANFITRION_ID);
        assertThat(editada.getEditadoPor()).isEqualTo(ADMINISTRADOR_ID);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.usuario()).isEqualTo(String.valueOf(ADMINISTRADOR_ID));
        assertThat(evento.accion()).isEqualTo(AccionCritica.ACCION_ADMINISTRATIVA);
        assertThat(evento.datos()).containsEntry("operacion", "editar");
    }

    @Test
    @DisplayName("Si la publicación no existe lanza 404 y no guarda nada")
    void rechazaPublicacionInexistente() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editar(PUBLICACION_ID, ANFITRION_ID, false, soloTitulo("Casa")))
                .isInstanceOf(PublicacionNoEncontradaException.class)
                .hasMessage("No encontramos esa publicación.");
        verify(publicaciones, never()).save(any(Publicacion.class));
    }

    @Test
    @DisplayName("Al editar con un tipo inexistente lanza la excepción y deja la publicación como estaba")
    void editarRechazaTipoInexistente() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));
        SolicitudBorrador cambios = new SolicitudBorrador("Casa renovada", null, null, null, "CASTILLO", null,
                null, null, null, null);

        assertThatThrownBy(() -> servicio.editar(PUBLICACION_ID, ANFITRION_ID, false, cambios))
                .isInstanceOf(TipoAlojamientoNoValidoException.class);
        assertThat(existente.getTitulo()).isEqualTo("Casa en la playa");
        verify(publicaciones, never()).save(any(Publicacion.class));
    }

    @Test
    @DisplayName("El dueño consulta su publicación")
    void duenoConsultaSuPublicacion() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));

        assertThat(servicio.consultar(PUBLICACION_ID, ANFITRION_ID, false)).isSameAs(existente);
    }

    @Test
    @DisplayName("Lista las publicaciones del anfitrión tal como las ordena el repositorio")
    void listaLasPublicacionesDelAnfitrion() {
        Publicacion reciente = publicacionDe(ANFITRION_ID);
        Publicacion antigua = publicacionDe(ANFITRION_ID);
        when(publicaciones.findByAnfitrionIdOrderByCreadoEnDesc(ANFITRION_ID)).thenReturn(List.of(reciente, antigua));

        assertThat(servicio.listarDelAnfitrion(ANFITRION_ID)).containsExactly(reciente, antigua);
    }

    @Test
    @DisplayName("Otro anfitrión no puede consultar un borrador ajeno: lanza 403")
    void otroAnfitrionNoPuedeConsultar() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(publicacionDe(ANFITRION_ID)));

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, OTRO_ANFITRION_ID, false))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("El administrador consulta cualquier publicación")
    void administradorConsultaCualquierPublicacion() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));

        assertThat(servicio.consultar(PUBLICACION_ID, ADMINISTRADOR_ID, true)).isSameAs(existente);
    }

    @Test
    @DisplayName("Consultar una publicación que no existe lanza 404")
    void consultarInexistenteLanza404() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(PUBLICACION_ID, ANFITRION_ID, false))
                .isInstanceOf(PublicacionNoEncontradaException.class);
    }

    @Test
    @DisplayName("El dueño elimina su borrador y deja un evento de publicación con la operación eliminar")
    void duenoEliminaSuBorrador() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));

        servicio.eliminar(PUBLICACION_ID, ANFITRION_ID, false);

        verify(publicaciones).delete(existente);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.usuario()).isEqualTo("7");
        assertThat(evento.accion()).isEqualTo(AccionCritica.PUBLICACION);
        assertThat(evento.entidadId()).isEqualTo(String.valueOf(PUBLICACION_ID));
        assertThat(evento.datos()).containsEntry("operacion", "eliminar");
    }

    @Test
    @DisplayName("Otro anfitrión no puede eliminar la publicación: lanza 403 y no borra ni audita nada")
    void otroAnfitrionNoPuedeEliminar() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(publicacionDe(ANFITRION_ID)));

        assertThatThrownBy(() -> servicio.eliminar(PUBLICACION_ID, OTRO_ANFITRION_ID, false))
                .isInstanceOf(AccesoDenegadoException.class);
        verify(publicaciones, never()).delete(any(Publicacion.class));
        verify(publicador, never()).publicar(any(EventoCritico.class));
    }

    @Test
    @DisplayName("El administrador elimina cualquier borrador y queda registrado como acción administrativa")
    void administradorEliminaCualquierBorrador() {
        Publicacion existente = publicacionDe(ANFITRION_ID);
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.of(existente));

        servicio.eliminar(PUBLICACION_ID, ADMINISTRADOR_ID, true);

        verify(publicaciones).delete(existente);
        EventoCritico evento = eventoPublicado();
        assertThat(evento.usuario()).isEqualTo(String.valueOf(ADMINISTRADOR_ID));
        assertThat(evento.accion()).isEqualTo(AccionCritica.ACCION_ADMINISTRATIVA);
        assertThat(evento.datos()).containsEntry("operacion", "eliminar");
    }

    @Test
    @DisplayName("Eliminar una publicación que no existe lanza 404 y no borra nada")
    void eliminarInexistenteLanza404() {
        when(publicaciones.findById(PUBLICACION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(PUBLICACION_ID, ANFITRION_ID, false))
                .isInstanceOf(PublicacionNoEncontradaException.class);
        verify(publicaciones, never()).delete(any(Publicacion.class));
    }

    private static Publicacion publicacionDe(Long anfitrionId) {
        return Publicacion.borrador(anfitrionId, new DatosBorrador("Casa en la playa", null, "Santa Marta", null,
                TipoAlojamiento.CASA, 4, null, Set.of("Piscina"), null, List.of("https://imagenes.com/1.jpg")));
    }

    private static SolicitudBorrador soloTitulo(String titulo) {
        return new SolicitudBorrador(titulo, null, null, null, null, null, null, null, null, null);
    }

    private EventoCritico eventoPublicado() {
        ArgumentCaptor<EventoCritico> captor = ArgumentCaptor.forClass(EventoCritico.class);
        verify(publicador).publicar(captor.capture());
        return captor.getValue();
    }
}
