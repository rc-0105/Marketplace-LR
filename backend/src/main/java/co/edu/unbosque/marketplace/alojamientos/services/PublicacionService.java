package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.exceptions.CambioEstadoNoPermitidoException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.PoliticaCancelacionNoValidaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionIncompletaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEliminableException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.ServicioAdicionalRepetidoException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.ServicioNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.TipoAlojamientoNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.model.CondicionesReserva;
import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.ModoReserva;
import co.edu.unbosque.marketplace.alojamientos.model.PoliticaCancelacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.AnfitrionDePublicacion;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.compartido.consultas.RevisionReservasPublicacion;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.compartido.seguridad.Propiedad;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creación, consulta, edición y eliminación de publicaciones (RF-11, RF-12,
 * RF-13), y sus condiciones comerciales (RF-20, RF-21, RF-22).
 */
@Service
public class PublicacionService {

    private static final String ENTIDAD = "Publicacion";
    // Igual que el listado de cuentas para administración (SOF-84).
    private static final int MAXIMO_MODERACION = 50;
    private static final int MAXIMO_MENSAJE = 600;
    private static final List<EstadoPublicacion> MODERABLES = List.of(EstadoPublicacion.ACTIVA,
            EstadoPublicacion.PAUSADA, EstadoPublicacion.BLOQUEADA);

    private final PublicacionRepository publicaciones;
    private final ServicioRepository catalogo;
    private final TipoAlojamientoRepository tipos;
    private final PoliticaCancelacionRepository politicas;
    private final PublicadorEventos publicador;
    private final Notificador notificador;
    private final RevisionReservasPublicacion revisionReservas;

    public PublicacionService(PublicacionRepository publicaciones, ServicioRepository catalogo,
            TipoAlojamientoRepository tipos, PoliticaCancelacionRepository politicas, PublicadorEventos publicador,
            Notificador notificador, RevisionReservasPublicacion revisionReservas) {
        this.publicaciones = publicaciones;
        this.catalogo = catalogo;
        this.tipos = tipos;
        this.politicas = politicas;
        this.publicador = publicador;
        this.notificador = notificador;
        this.revisionReservas = revisionReservas;
    }

    /** Guarda la publicación en BORRADOR a nombre del anfitrión autenticado. */
    @Transactional
    public Publicacion crearBorrador(Long anfitrionId, SolicitudBorrador solicitud) {
        Publicacion publicacion = publicaciones.save(Publicacion.borrador(anfitrionId, datosDe(solicitud)));

        publicador.publicar(new EventoCritico(String.valueOf(anfitrionId), AccionCritica.PUBLICACION, ENTIDAD,
                String.valueOf(publicacion.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of("operacion", "crear_borrador")));
        return publicacion;
    }

    /**
     * Reemplaza los datos de la publicación. El anfitrión solo edita las suyas; el
     * administrador edita cualquiera y queda como acción administrativa.
     */
    @Transactional
    public Publicacion editar(Long publicacionId, Long usuarioId, boolean esAdministrador,
            SolicitudBorrador solicitud) {
        Publicacion publicacion = buscarConAcceso(publicacionId, usuarioId, esAdministrador);
        publicacion.editar(datosDe(solicitud), usuarioId);
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, usuarioId, esAdministrador, "editar");
        return guardada;
    }

    /** Reemplaza solo los servicios por los del catálogo indicados. */
    @Transactional
    public Publicacion actualizarServicios(Long publicacionId, Long usuarioId, boolean esAdministrador,
            List<Long> servicioIds) {
        Publicacion publicacion = buscarConAcceso(publicacionId, usuarioId, esAdministrador);
        publicacion.cambiarServicios(serviciosDe(servicioIds), usuarioId);
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, usuarioId, esAdministrador, "editar_servicios");
        return guardada;
    }

    /**
     * Reemplaza solo las imágenes; el orden enviado define la principal.
     */
    @Transactional
    public Publicacion actualizarImagenes(Long publicacionId, Long usuarioId, boolean esAdministrador,
            List<String> urls) {
        Publicacion publicacion = buscarConAcceso(publicacionId, usuarioId, esAdministrador);
        publicacion.cambiarImagenes(recortados(urls), usuarioId);
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, usuarioId, esAdministrador, "editar_imagenes");
        return guardada;
    }

    /**
     * Reemplaza solo las condiciones comerciales: tarifa de limpieza, modo de
     * reserva, política de cancelación y servicios adicionales.
     */
    @Transactional
    public Publicacion actualizarConfiguracion(Long publicacionId, Long usuarioId, boolean esAdministrador,
            SolicitudConfiguracion solicitud) {
        Publicacion publicacion = buscarConAcceso(publicacionId, usuarioId, esAdministrador);
        publicacion.configurar(condicionesDe(solicitud), usuarioId);
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, usuarioId, esAdministrador, "editar_configuracion");
        return guardada;
    }

    /** Detalle para el dueño o un administrador: un borrador no es público. */
    @Transactional(readOnly = true)
    public Publicacion consultar(Long publicacionId, Long usuarioId, boolean esAdministrador) {
        return buscarConAcceso(publicacionId, usuarioId, esAdministrador);
    }

    /**
     * Para otros módulos, 404 si la publicación no existe y 403 si no es del
     * anfitrión. No carga la publicación, solo su dueño.
     */
    @Transactional(readOnly = true)
    public void exigirPropietario(Long publicacionId, Long anfitrionId) {
        Long propietarioId = publicaciones.findAnfitrionById(publicacionId).map(AnfitrionDePublicacion::getAnfitrionId)
                .orElseThrow(PublicacionNoEncontradaException::new);
        Propiedad.exigirPropietario(propietarioId, anfitrionId);
    }

    /**
     * Para otros módulos: dueño y visibilidad de la publicación, o vacío si no existe. A diferencia de
     * {@link #exigirPropietario}, nunca lanza: quien consulta decide qué respuesta dar.
     */
    @Transactional(readOnly = true)
    public Optional<AccesoPublicacion> consultarAcceso(Long publicacionId) {
        return publicaciones.findAccesoById(publicacionId).map(acceso -> new AccesoPublicacion(
                acceso.getAnfitrionId(), acceso.getEstado() == EstadoPublicacion.ACTIVA));
    }

    /**
     * Publicaciones del anfitrión, la más reciente primero: de aquí elige cuál
     * editar.
     */
    @Transactional(readOnly = true)
    public List<Publicacion> listarDelAnfitrion(Long anfitrionId) {
        return publicaciones.findByAnfitrionIdOrderByCreadoEnDesc(anfitrionId);
    }

    /**
     * Borra la publicación con sus imágenes y servicios. Solo un BORRADOR: nunca
     * fue público, así que no tiene reservas ni reseñas que dependan de él.
     */
    @Transactional
    public void eliminar(Long publicacionId, Long usuarioId, boolean esAdministrador) {
        Publicacion publicacion = buscarConAcceso(publicacionId, usuarioId, esAdministrador);
        if (publicacion.getEstado() != EstadoPublicacion.BORRADOR) {
            throw new PublicacionNoEliminableException();
        }
        publicaciones.delete(publicacion);
        auditar(publicacionId, usuarioId, esAdministrador, "eliminar");
    }

    /**
     * Un borrador completo pasa a PENDIENTE_REVISION y no sale al público hasta que
     * un administrador lo apruebe ; uno pausado vuelve directo a ACTIVA.
     */
    @Transactional
    public Publicacion activar(Long publicacionId, Long anfitrionId) {
        Publicacion publicacion = buscarConAcceso(publicacionId, anfitrionId, false);
        String operacion = switch (publicacion.getEstado()) {
        case BORRADOR -> {
            List<String> faltantes = publicacion.camposFaltantesParaActivar();
            if (!faltantes.isEmpty()) {
                throw new PublicacionIncompletaException(faltantes);
            }
            publicacion.enviarARevision();
            yield "enviar_a_revision";
        }
        case PAUSADA -> {
            publicacion.reactivar();
            yield "reactivar";
        }
        case PENDIENTE_REVISION ->
            throw new CambioEstadoNoPermitidoException("La publicación ya está esperando revisión.");
        case ACTIVA -> throw new CambioEstadoNoPermitidoException("La publicación ya está activa.");
        case BLOQUEADA -> throw new CambioEstadoNoPermitidoException(
                "Esta publicación fue bloqueada por un administrador. Contacta a soporte para revisarla.");
        };
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, anfitrionId, false, operacion);
        notificarEstado(publicacionId, guardada, "");
        return guardada;
    }

    /** Una publicación ACTIVA pasa a PAUSADA y sale de las búsquedas */
    @Transactional
    public Publicacion pausar(Long publicacionId, Long anfitrionId) {
        Publicacion publicacion = buscarConAcceso(publicacionId, anfitrionId, false);
        if (publicacion.getEstado() != EstadoPublicacion.ACTIVA) {
            throw new CambioEstadoNoPermitidoException("Solo se puede pausar una publicación activa.");
        }
        publicacion.pausar();
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, anfitrionId, false, "pausar");
        notificarEstado(publicacionId, guardada, "");
        return guardada;
    }

    /**
     * Publicaciones ACTIVAS de la ciudad: de ahí parte la búsqueda, que luego
     * filtra por fechas
     */
    @Transactional(readOnly = true)
    public List<CandidatoBusqueda> buscarCandidatos(String ciudad) {
        return publicaciones.findByEstadoAndCiudadIgnoreCaseOrderById(EstadoPublicacion.ACTIVA, ciudad).stream()
                .map(CandidatoBusqueda::desde).toList();
    }

    /** Publicaciones ACTIVAS, las más recientes primero: de ahí parte el Inicio público. */
    @Transactional(readOnly = true)
    public List<CandidatoBusqueda> listarActivasRecientes() {
        List<CandidatoBusqueda> candidatos = new ArrayList<>();
        for (Publicacion publicacion : publicaciones.findByEstadoOrderByIdDesc(EstadoPublicacion.ACTIVA)) {
            candidatos.add(CandidatoBusqueda.desde(publicacion));
        }
        return candidatos;
    }

    /** Detalle público: solo una publicación ACTIVA; si no existe o no está activa responde 404. */
    @Transactional(readOnly = true)
    public DetalleAlojamiento consultarActiva(Long publicacionId) {
        return DetalleAlojamiento.desde(buscarActiva(publicacionId));
    }

    /**
     * Si una publicación ACTIVA confirma la reserva de una vez o exige aprobación del anfitrión
     * (RF-21): SOF-33 solo soporta la confirmación inmediata. No expone {@link ModoReserva}
     * fuera del módulo (ADR-2); misma regla de visibilidad que {@link #consultarActiva}.
     */
    @Transactional(readOnly = true)
    public boolean esModoInmediato(Long publicacionId) {
        return buscarActiva(publicacionId).getModoReserva() == ModoReserva.INMEDIATA;
    }

    /**
     * Alojamientos de las reservas de un huésped (SOF-35), por id. A diferencia de {@link #consultarActiva} no
     * filtra por estado: la reserva sigue siendo del huésped aunque luego la publicación se pause. Un id que ya
     * no existe simplemente no aparece en el resultado.
     */
    @Transactional(readOnly = true)
    public Map<Long, AlojamientoReservado> consultarReservados(Collection<Long> publicacionIds) {
        return publicaciones.findAllById(publicacionIds).stream()
                .collect(Collectors.toUnmodifiableMap(Publicacion::getId, AlojamientoReservado::desde));
    }

    /**
     * Si un administrador bloqueó la publicación (SOF-83): una reserva pendiente suya ya no se cobra. Un id que
     * ya no existe no cuenta como bloqueado.
     */
    @Transactional(readOnly = true)
    public boolean estaBloqueada(Long publicacionId) {
        return publicaciones.findById(publicacionId)
                .map(publicacion -> publicacion.getEstado() == EstadoPublicacion.BLOQUEADA)
                .orElse(false);
    }

    // Una publicación inexistente o que no está ACTIVA responde el mismo 404: a quien reserva o
    // cotiza no se le revela si existe en otro estado.
    private Publicacion buscarActiva(Long publicacionId) {
        Optional<Publicacion> encontrada = publicaciones.findById(publicacionId);
        if (encontrada.isEmpty() || encontrada.get().getEstado() != EstadoPublicacion.ACTIVA) {
            throw new PublicacionNoEncontradaException();
        }
        return encontrada.get();
    }

    /**
     * Publicaciones que esperan revisión, la que lleva más tiempo esperando primero
     *
     */
    @Transactional(readOnly = true)
    public List<Publicacion> listarPendientes() {
        return publicaciones.findByEstado(EstadoPublicacion.PENDIENTE_REVISION).stream()
                .sorted(Comparator.comparing(
                        (Publicacion p) -> Optional.ofNullable(p.getEnviadaARevisionEn()).orElse(p.getCreadoEn()))
                        .thenComparing(Publicacion::getId))
                .toList();
    }

    /** Una publicación pendiente pasa a ACTIVA y sale al público */
    @Transactional
    public Publicacion aprobar(Long publicacionId, Long administradorId) {
        Publicacion publicacion = buscarPendiente(publicacionId);
        publicacion.aprobar();
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, administradorId, true, "aprobar");
        notificarEstado(publicacionId, guardada, "");
        return guardada;
    }

    /**
     * Una publicación pendiente vuelve a BORRADOR con el motivo que verá el
     * anfitrión
     */
    @Transactional
    public Publicacion devolver(Long publicacionId, Long administradorId, String observaciones) {
        Publicacion publicacion = buscarPendiente(publicacionId);
        publicacion.devolver(observaciones.trim());
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, administradorId, true, "devolver");
        notificarEstado(publicacionId, guardada, " Revisa las observaciones del administrador.");
        return guardada;
    }

    /**
     * Moderación (SOF-83): publicaciones ACTIVA, PAUSADA o BLOQUEADA cuyo título contiene el texto, como
     * máximo 50. Sin texto devuelve las primeras por título.
     */
    @Transactional(readOnly = true)
    public List<Publicacion> listarParaModeracion(String texto) {
        String filtro = texto == null ? "" : texto.strip();
        // Escapados para LIKE: quien busca "_" quiere ese carácter, no cualquiera.
        String literal = filtro.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return publicaciones.buscarParaModeracion(literal, MODERABLES, PageRequest.of(0, MAXIMO_MODERACION));
    }

    /**
     * Bloquea una publicación ACTIVA o PAUSADA con información engañosa (SOF-83, AC1): pasa a BLOQUEADA con el
     * motivo, sale de las búsquedas y no admite reservas nuevas. En la misma transacción sus reservas futuras
     * confirmadas pasan a revisión manual (AC2), y el anfitrión recibe el motivo.
     */
    @Transactional
    public Publicacion bloquear(Long publicacionId, Long administradorId, String motivo) {
        Publicacion publicacion = publicaciones.findById(publicacionId)
                .orElseThrow(PublicacionNoEncontradaException::new);
        if (publicacion.getEstado() != EstadoPublicacion.ACTIVA
                && publicacion.getEstado() != EstadoPublicacion.PAUSADA) {
            throw new CambioEstadoNoPermitidoException("Solo se puede bloquear una publicación activa o pausada.");
        }
        publicacion.bloquear(motivo.trim(), Instant.now());
        Publicacion guardada = publicaciones.save(publicacion);
        revisionReservas.enviarARevision(publicacionId);
        auditar(publicacionId, administradorId, true, "bloquear");
        notificarEstado(publicacionId, guardada, " Motivo: " + guardada.getMotivoBloqueo());
        return guardada;
    }

    private Publicacion buscarPendiente(Long publicacionId) {
        Publicacion publicacion = publicaciones.findById(publicacionId)
                .orElseThrow(PublicacionNoEncontradaException::new);
        if (publicacion.getEstado() != EstadoPublicacion.PENDIENTE_REVISION) {
            throw new CambioEstadoNoPermitidoException("Solo se puede revisar una publicación pendiente de revisión.");
        }
        return publicacion;
    }

    // La propiedad va antes que el contenido: a quien no es dueño no se le revela
    // nada más.
    private Publicacion buscarConAcceso(Long publicacionId, Long usuarioId, boolean esAdministrador) {
        Publicacion publicacion = publicaciones.findById(publicacionId)
                .orElseThrow(PublicacionNoEncontradaException::new);
        if (!esAdministrador) {
            Propiedad.exigirPropietario(publicacion.getAnfitrionId(), usuarioId);
        }
        return publicacion;
    }

    private void auditar(Long publicacionId, Long usuarioId, boolean esAdministrador, String operacion) {
        AccionCritica accion = esAdministrador ? AccionCritica.ACCION_ADMINISTRATIVA : AccionCritica.PUBLICACION;
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), accion, ENTIDAD, String.valueOf(publicacionId),
                ResultadoEvento.EXITO, Instant.now(), Map.of("operacion", operacion)));
    }

    // SOF-37 AC2: el anfitrión dueño recibe el nuevo estado, también cuando el cambio lo hizo él mismo.
    private void notificarEstado(Long publicacionId, Publicacion publicacion, String complemento) {
        String mensaje = "Tu publicación «%s» ahora está %s.%s".formatted(publicacion.getTitulo(),
                nombreLegible(publicacion.getEstado()), complemento);
        notificador.notificar(new NuevaNotificacion(publicacion.getAnfitrionId(), TipoNotificacion.PUBLICACION_ESTADO,
                recortado(mensaje), ENTIDAD, publicacionId));
    }

    // La columna admite 600 caracteres; un título largo con un motivo de bloqueo de 500 puede pasarse y el
    // aviso se perdería. Se recorta el final, que es el motivo; el anfitrión lo ve completo en su publicación.
    private static String recortado(String mensaje) {
        return mensaje.length() <= MAXIMO_MENSAJE ? mensaje : mensaje.substring(0, MAXIMO_MENSAJE - 1) + "…";
    }

    private static String nombreLegible(EstadoPublicacion estado) {
        return switch (estado) {
        case BORRADOR -> "borrador";
        case PENDIENTE_REVISION -> "pendiente de revisión";
        case ACTIVA -> "activa";
        case PAUSADA -> "pausada";
        case BLOQUEADA -> "bloqueada";
        };
    }

    private DatosBorrador datosDe(SolicitudBorrador solicitud) {
        return new DatosBorrador(solicitud.titulo().trim(), textoOpcional(solicitud.descripcion()),
                textoOpcional(solicitud.ciudad()), textoOpcional(solicitud.direccion()), tipoDe(solicitud.tipo()),
                solicitud.capacidad(), solicitud.precioNoche(), serviciosDe(solicitud.servicios()),
                textoOpcional(solicitud.reglas()), recortados(solicitud.imagenes()));
    }

    // Los ids repetidos cuentan una vez; si alguno no está en el catálogo se
    // rechaza la solicitud completa.
    private Set<Servicio> serviciosDe(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> unicos = new LinkedHashSet<>(ids);
        List<Servicio> encontrados = catalogo.findAllById(unicos);
        if (encontrados.size() != unicos.size()) {
            throw new ServicioNoValidoException();
        }
        return new LinkedHashSet<>(encontrados);
    }

    private TipoAlojamiento tipoDe(Long tipoId) {
        if (tipoId == null) {
            return null;
        }
        return tipos.findById(tipoId).orElseThrow(TipoAlojamientoNoValidoException::new);
    }

    // Todo se valida antes de tocar la publicación: si algo falla, queda como
    // estaba.
    private CondicionesReserva condicionesDe(SolicitudConfiguracion solicitud) {
        ModoReserva modo = solicitud.modoReserva() == null ? ModoReserva.INMEDIATA
                : ModoReserva.valueOf(solicitud.modoReserva());
        PoliticaCancelacion politica = solicitud.politicaCancelacion() == null ? null
                : politicas.findById(solicitud.politicaCancelacion())
                        .orElseThrow(PoliticaCancelacionNoValidaException::new);
        return new CondicionesReserva(solicitud.tarifaLimpieza(), modo, politica,
                adicionalesDe(solicitud.serviciosAdicionales()));
    }

    private static List<CondicionesReserva.Adicional> adicionalesDe(
            List<SolicitudConfiguracion.ServicioAdicional> solicitados) {
        if (solicitados == null) {
            return List.of();
        }
        Set<String> nombres = new HashSet<>();
        List<CondicionesReserva.Adicional> resultado = new ArrayList<>();
        for (SolicitudConfiguracion.ServicioAdicional solicitado : solicitados) {
            String nombre = solicitado.nombre().trim();
            if (!nombres.add(nombre.toLowerCase(Locale.ROOT))) {
                throw new ServicioAdicionalRepetidoException();
            }
            resultado.add(new CondicionesReserva.Adicional(nombre, solicitado.costo()));
        }
        return List.copyOf(resultado);
    }

    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static List<String> recortados(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        List<String> resultado = new ArrayList<>();
        for (String valor : valores) {
            resultado.add(valor.trim());
        }
        return List.copyOf(resultado);
    }
}