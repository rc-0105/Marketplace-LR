package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static net.logstash.logback.argument.StructuredArguments.kv;

import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Escribe cada evento crítico como una línea JSON en el logger de auditoría
 * (RF-45). El identificador de correlación lo agrega el encoder desde el MDC.
 */
@Component
public class RegistroEventosCriticos {

    static final String LOGGER = "AUDITORIA";

    private static final Logger AUDITORIA = LoggerFactory.getLogger(LOGGER);

    // Escucha síncrona a propósito: corre en el hilo de la petición y por eso
    // hereda el MDC. Si algún día pasa a @Async, hay que copiar el MDC al hilo.
    @EventListener
    public void registrar(EventoCritico evento) {
        AUDITORIA.info("evento_critico",
                kv("usuario", evento.usuario()),
                kv("accion", evento.accion()),
                kv("entidad", evento.entidad()),
                kv("entidadId", evento.entidadId()),
                kv("resultado", evento.resultado()),
                kv("fecha", evento.fecha().toString()),
                kv("datos", SanitizadorDatos.sanear(evento.datos())));
    }
}
