package co.edu.unbosque.marketplace.auditoria.infraestructura;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import net.logstash.logback.encoder.LogstashEncoder;
import org.slf4j.LoggerFactory;

/** Captura lo que llega al logger de auditoría y lo codifica igual que en producción. */
final class CapturaAuditoria implements AutoCloseable {

    private final Logger auditoria = (Logger) LoggerFactory.getLogger(RegistroEventosCriticos.LOGGER);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private final LogstashEncoder codificador = new LogstashEncoder();
    private final ObjectMapper json = new ObjectMapper();

    CapturaAuditoria() {
        codificador.setContext(auditoria.getLoggerContext());
        codificador.start();
        eventos.start();
        auditoria.addAppender(eventos);
    }

    static EventoCritico evento(AccionCritica accion, Map<String, Object> datos) {
        return new EventoCritico("usuario-42", accion, "Reserva", "R-1001",
                ResultadoEvento.EXITO, Instant.parse("2026-10-22T21:00:00Z"), datos);
    }

    int cantidad() {
        return eventos.list.size();
    }

    String texto(int indice) {
        return new String(codificador.encode(eventos.list.get(indice)), StandardCharsets.UTF_8);
    }

    JsonNode json(int indice) throws JsonProcessingException {
        return json.readTree(texto(indice));
    }

    @Override
    public void close() {
        auditoria.detachAppender(eventos);
        codificador.stop();
    }
}
