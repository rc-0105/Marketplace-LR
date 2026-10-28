package co.edu.unbosque.marketplace.alojamientos.services;

import co.edu.unbosque.marketplace.alojamientos.exceptions.TipoAlojamientoNoValidoException;
import co.edu.unbosque.marketplace.alojamientos.model.DatosBorrador;
import co.edu.unbosque.marketplace.alojamientos.model.Publicacion;
import co.edu.unbosque.marketplace.alojamientos.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.repository.PublicacionRepository;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creación de publicaciones por parte de los anfitriones (RF-11, RF-12). */
@Service
public class PublicacionService {

    private static final String ENTIDAD = "Publicacion";

    private final PublicacionRepository publicaciones;
    private final PublicadorEventos publicador;

    public PublicacionService(PublicacionRepository publicaciones, PublicadorEventos publicador) {
        this.publicaciones = publicaciones;
        this.publicador = publicador;
    }

    /** Guarda la publicación en BORRADOR a nombre del anfitrión autenticado. */
    @Transactional
    public Publicacion crearBorrador(Long anfitrionId, SolicitudBorrador solicitud) {
        DatosBorrador datos = new DatosBorrador(
                solicitud.titulo().trim(),
                textoOpcional(solicitud.descripcion()),
                textoOpcional(solicitud.ciudad()),
                textoOpcional(solicitud.direccion()),
                tipoDe(solicitud.tipo()),
                solicitud.capacidad(),
                solicitud.precioNoche(),
                new LinkedHashSet<>(recortados(solicitud.servicios())),
                textoOpcional(solicitud.reglas()),
                recortados(solicitud.imagenes()));

        Publicacion publicacion = publicaciones.save(Publicacion.borrador(anfitrionId, datos));

        publicador.publicar(new EventoCritico(String.valueOf(anfitrionId), AccionCritica.PUBLICACION, ENTIDAD,
                String.valueOf(publicacion.getId()), ResultadoEvento.EXITO, Instant.now(),
                Map.of("operacion", "crear_borrador")));
        return publicacion;
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
