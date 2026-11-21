package co.edu.unbosque.marketplace.resenas.services;

import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservaParaResena;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaParaResena;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.compartido.exceptions.AccesoDenegadoException;
import co.edu.unbosque.marketplace.resenas.exceptions.ReservaNoEncontradaParaResenaException;
import co.edu.unbosque.marketplace.resenas.exceptions.ReservaNoResenableException;
import co.edu.unbosque.marketplace.resenas.exceptions.ResenaYaExisteException;
import co.edu.unbosque.marketplace.resenas.model.Resena;
import co.edu.unbosque.marketplace.resenas.repository.ResenaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Creación de la reseña y la calificación de un huésped tras su estancia (SOF-80, HU-35, RF-53). Solo el
 * huésped titular reseña su propia reserva, y solo cuando está COMPLETADA y todavía no tiene reseña. Cada
 * reseña creada, y cada intento ajeno, queda en la auditoría.
 */
@Service
public class ResenaService {

    private static final String ENTIDAD = "Resena";

    private final ResenaRepository resenas;
    private final ConsultaReservaParaResena reservas;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public ResenaService(ResenaRepository resenas, ConsultaReservaParaResena reservas,
            PublicadorEventos publicador, Clock reloj) {
        this.resenas = resenas;
        this.reservas = reservas;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    /**
     * Crea la reseña de la reserva de {@code huespedId}. Una reserva ajena responde 403 y el intento queda
     * auditado (AC4); una que no está COMPLETADA responde 409 (AC2), y una que ya tiene reseña también (AC3).
     */
    public Resena crear(Long reservaId, Long huespedId, int calificacion, String contenido) {
        Optional<ReservaParaResena> consulta = reservas.consultar(reservaId);
        if (consulta.isEmpty()) {
            throw new ReservaNoEncontradaParaResenaException();
        }
        ReservaParaResena reserva = consulta.get();
        if (!reserva.huespedId().equals(huespedId)) {
            auditar(reservaId, huespedId, ResultadoEvento.FALLO, Map.of("resultado", "intento_no_autorizado"));
            throw new AccesoDenegadoException();
        }
        if (!reserva.estaCompletada()) {
            throw new ReservaNoResenableException(mensajeNoResenable(reserva.estado()));
        }
        if (resenas.existsByReservaId(reservaId)) {
            throw new ResenaYaExisteException();
        }

        Resena resena = Resena.nueva(reservaId, huespedId, reserva.publicacionId(), Instant.now(reloj),
                calificacion, contenido);
        Resena guardada = guardar(resena);
        auditar(reservaId, huespedId, ResultadoEvento.EXITO, Map.of(
                "resenaId", guardada.getId(),
                "publicacionId", reserva.publicacionId(),
                "calificacion", calificacion));
        return guardada;
    }

    // La restricción única por reserva decide cuando dos solicitudes llegan a la vez: la que pierde recibe
    // el mismo 409 que si la reseña ya hubiera existido.
    private Resena guardar(Resena resena) {
        try {
            return resenas.saveAndFlush(resena);
        } catch (DataIntegrityViolationException error) {
            if (resenas.existsByReservaId(resena.getReservaId())) {
                throw new ResenaYaExisteException();
            }
            throw error;
        }
    }

    private String mensajeNoResenable(String estado) {
        if ("CONFIRMADA".equals(estado)) {
            return "Todavía no puedes reseñar: la estancia aún no ha terminado.";
        }
        return "Solo puedes reseñar una reserva cuya estancia ya terminó.";
    }

    private void auditar(Long reservaId, Long huespedId, ResultadoEvento resultado, Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", "crear");
        publicador.publicar(new EventoCritico(String.valueOf(huespedId), AccionCritica.RESENA, ENTIDAD,
                String.valueOf(reservaId), resultado, Instant.now(reloj), contexto));
    }
}
