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
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.seguridad.Propiedad;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creación, consulta, edición y eliminación de publicaciones (RF-11, RF-12, RF-13),
 * y sus condiciones comerciales (RF-20, RF-21, RF-22).
 */
@Service
public class PublicacionService {

    private static final String ENTIDAD = "Publicacion";

    private final PublicacionRepository publicaciones;
    private final ServicioRepository catalogo;
    private final TipoAlojamientoRepository tipos;
    private final PoliticaCancelacionRepository politicas;
    private final PublicadorEventos publicador;

    public PublicacionService(PublicacionRepository publicaciones, ServicioRepository catalogo,
            TipoAlojamientoRepository tipos, PoliticaCancelacionRepository politicas, PublicadorEventos publicador) {
        this.publicaciones = publicaciones;
        this.catalogo = catalogo;
        this.tipos = tipos;
        this.politicas = politicas;
        this.publicador = publicador;
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
     * Reemplaza los datos de la publicación (RF-13). El anfitrión solo edita las
     * suyas; el administrador edita cualquiera y queda como acción administrativa.
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

    /** Reemplaza solo los servicios por los del catálogo indicados (RF-18). */
    @Transactional
    public Publicacion actualizarServicios(Long publicacionId, Long usuarioId, boolean esAdministrador,
            List<Long> servicioIds) {
        Publicacion publicacion = buscarConAcceso(publicacionId, usuarioId, esAdministrador);
        publicacion.cambiarServicios(serviciosDe(servicioIds), usuarioId);
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, usuarioId, esAdministrador, "editar_servicios");
        return guardada;
    }

    /** Reemplaza solo las imágenes; el orden enviado define la principal (RF-19). */
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
     * reserva, política de cancelación y servicios adicionales (RF-20, RF-21, RF-22).
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

    /** Publicaciones del anfitrión, la más reciente primero: de aquí elige cuál editar. */
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
     * un administrador lo apruebe (RF-14); uno pausado vuelve directo a ACTIVA (RF-17).
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
        };
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, anfitrionId, false, operacion);
        return guardada;
    }

    /** Una publicación ACTIVA pasa a PAUSADA y sale de las búsquedas (RF-17). */
    @Transactional
    public Publicacion pausar(Long publicacionId, Long anfitrionId) {
        Publicacion publicacion = buscarConAcceso(publicacionId, anfitrionId, false);
        if (publicacion.getEstado() != EstadoPublicacion.ACTIVA) {
            throw new CambioEstadoNoPermitidoException("Solo se puede pausar una publicación activa.");
        }
        publicacion.pausar();
        Publicacion guardada = publicaciones.save(publicacion);
        auditar(publicacionId, anfitrionId, false, "pausar");
        return guardada;
    }

    // La propiedad va antes que el contenido: a quien no es dueño no se le revela nada más.
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
        publicador.publicar(new EventoCritico(String.valueOf(usuarioId), accion, ENTIDAD,
                String.valueOf(publicacionId), ResultadoEvento.EXITO, Instant.now(), Map.of("operacion", operacion)));
    }

    private DatosBorrador datosDe(SolicitudBorrador solicitud) {
        return new DatosBorrador(
                solicitud.titulo().trim(),
                textoOpcional(solicitud.descripcion()),
                textoOpcional(solicitud.ciudad()),
                textoOpcional(solicitud.direccion()),
                tipoDe(solicitud.tipo()),
                solicitud.capacidad(),
                solicitud.precioNoche(),
                serviciosDe(solicitud.servicios()),
                textoOpcional(solicitud.reglas()),
                recortados(solicitud.imagenes()));
    }

    // Los ids repetidos cuentan una vez; si alguno no está en el catálogo se rechaza la solicitud completa.
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

    // Todo se valida antes de tocar la publicación: si algo falla, queda como estaba.
    private CondicionesReserva condicionesDe(SolicitudConfiguracion solicitud) {
        ModoReserva modo = solicitud.modoReserva() == null
                ? ModoReserva.INMEDIATA
                : ModoReserva.valueOf(solicitud.modoReserva());
        PoliticaCancelacion politica = solicitud.politicaCancelacion() == null
                ? null
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
