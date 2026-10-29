package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEliminableException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.PublicacionNoEncontradaException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.ServicioNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.exceptions.TipoAlojamientoNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.EstadoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.Servicio;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.alojamientos.repository.ServicioRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.seguridad.Propiedad;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creación, consulta, edición y eliminación de publicaciones (RF-11, RF-12, RF-13). */
@Service
public class PublicacionService {

    private static final String ENTIDAD = "Publicacion";

    private final PublicacionRepository publicaciones;
    private final ServicioRepository catalogo;
    private final PublicadorEventos publicador;

    public PublicacionService(PublicacionRepository publicaciones, ServicioRepository catalogo,
            PublicadorEventos publicador) {
        this.publicaciones = publicaciones;
        this.catalogo = catalogo;
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

    /** Detalle para el dueño o un administrador: un borrador no es público. */
    @Transactional(readOnly = true)
    public Publicacion consultar(Long publicacionId, Long usuarioId, boolean esAdministrador) {
        return buscarConAcceso(publicacionId, usuarioId, esAdministrador);
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

    // Los ids repetidos cuentan una vez; si alguno no está en el catálogo se rechaza todo.
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

    private static TipoAlojamiento tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        String buscado = tipo.trim();
        for (TipoAlojamiento candidato : TipoAlojamiento.values()) {
            if (candidato.name().equals(buscado)) {
                return candidato;
            }
        }
        throw new TipoAlojamientoNoValidoException();
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
