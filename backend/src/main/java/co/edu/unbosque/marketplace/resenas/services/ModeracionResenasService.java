package co.edu.unbosque.marketplace.resenas.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.notificaciones.Notificador;
import co.edu.unbosque.marketplace.compartido.notificaciones.NuevaNotificacion;
import co.edu.unbosque.marketplace.compartido.notificaciones.TipoNotificacion;
import co.edu.unbosque.marketplace.resenas.exceptions.ResenaNoEncontradaException;
import co.edu.unbosque.marketplace.resenas.exceptions.ResenaYaOcultaException;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import co.edu.unbosque.marketplace.resenas.repository.ResenaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderación de reseñas inapropiadas (SOF-82, HU-37, RF-55). El administrador oculta una reseña con un
 * motivo: deja de mostrarse y de contar en el promedio, pero la fila nunca se elimina. Cada reseña
 * ocultada queda en la auditoría con el administrador, la reseña, la fecha y el motivo, y su autor recibe una
 * notificación que le explica el motivo.
 */
@Service
public class ModeracionResenasService {

    private static final String ENTIDAD = "Resena";

    private final ResenaRepository resenas;
    private final PublicadorEventos publicador;
    private final Notificador notificador;
    private final Clock reloj;

    public ModeracionResenasService(ResenaRepository resenas, PublicadorEventos publicador,
            Notificador notificador, Clock reloj) {
        this.resenas = resenas;
        this.publicador = publicador;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    /** Oculta la reseña con su motivo (AC1, AC2, AC3). Una reseña inexistente responde 404; una ya oculta, 409. */
    @Transactional
    public Resena ocultar(Long resenaId, Long administradorId, String motivo) {
        Optional<Resena> encontrada = resenas.findById(resenaId);
        if (encontrada.isEmpty()) {
            throw new ResenaNoEncontradaException();
        }
        Resena resena = encontrada.get();
        if (resena.estaOculta()) {
            throw new ResenaYaOcultaException();
        }
        resena.ocultar(motivo);
        Resena guardada = resenas.save(resena);
        publicador.publicar(new EventoCritico(String.valueOf(administradorId), AccionCritica.ACCION_ADMINISTRATIVA,
                ENTIDAD, String.valueOf(resenaId), ResultadoEvento.EXITO, Instant.now(reloj),
                Map.of("operacion", "ocultar", "publicacionId", guardada.getPublicacionId(), "motivo", motivo)));
        notificador.notificar(new NuevaNotificacion(guardada.getHuespedId(), TipoNotificacion.RESENA_OCULTA,
                "Ocultamos tu reseña porque incumple las políticas de la plataforma. Motivo: %s".formatted(motivo),
                ENTIDAD, resenaId));
        return guardada;
    }
}
