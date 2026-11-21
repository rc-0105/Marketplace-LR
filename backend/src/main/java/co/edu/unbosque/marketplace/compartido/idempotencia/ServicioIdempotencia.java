package co.edu.unbosque.marketplace.compartido.idempotencia;

import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaInvalidaException;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.ClaveIdempotenciaReutilizadaException;
import co.edu.unbosque.marketplace.compartido.idempotencia.exceptions.SolicitudEnProcesoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Idempotencia de solicitudes (SOF-77, HU-32): una misma {@code Idempotency-Key} ejecuta la operación una
 * sola vez y las repeticiones reciben la respuesta guardada. No es transaccional a propósito: reclama la
 * clave en una transacción corta, ejecuta la operación con sus propias transacciones y cierra en otra, sin
 * retener una conexión durante la operación (que en SOF-34 incluirá la llamada a la pasarela).
 */
@Service
public class ServicioIdempotencia {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioIdempotencia.class);

    public static final String CABECERA = "Idempotency-Key";

    private static final Pattern UUID_CANONICO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final RegistroIdempotencia registro;
    private final Clock reloj;
    private final ObjectMapper mapeador;
    private final Duration vigencia;
    private final Duration lease;
    private final int segundosReintento;

    public ServicioIdempotencia(RegistroIdempotencia registro, Clock reloj, ObjectMapper mapeador,
            @Value("${marketplace.idempotencia.horas-vigencia:24}") long horasVigencia,
            @Value("${marketplace.idempotencia.segundos-reclamo:120}") long segundosReclamo,
            @Value("${marketplace.idempotencia.segundos-reintento:2}") int segundosReintento) {
        this.registro = registro;
        this.reloj = reloj;
        this.mapeador = mapeador;
        this.vigencia = Duration.ofHours(horasVigencia);
        this.lease = Duration.ofSeconds(segundosReclamo);
        this.segundosReintento = segundosReintento;
    }

    /**
     * Ejecuta {@code operacion} una sola vez por clave y usuario. La primera vez guarda la respuesta con el
     * código {@code exito}; las siguientes la devuelven sin ejecutar nada.
     */
    public <T> RespuestaIdempotente ejecutar(SolicitudIdempotente solicitud, HttpStatus exito,
            Supplier<T> operacion) {
        if (!exito.is2xxSuccessful()) {
            // Solo se guardan respuestas 2xx (D10); los fallos viajan como excepciones y liberan la clave.
            throw new IllegalArgumentException("El código de éxito debe ser 2xx: " + exito.value());
        }
        UUID clave = validar(solicitud.clave());
        String huella = huella(solicitud.operacion(), solicitud.contenidoCanonico());
        // Dos vueltas: si quien tenía la clave la libera entre el reclamo y la lectura, se reclama otra vez.
        for (int intento = 0; intento < 2; intento++) {
            Optional<UUID> reclamo = registro.reclamar(solicitud.usuarioId(), clave, huella, Instant.now(reloj),
                    lease);
            if (reclamo.isPresent()) {
                return ejecutarReclamado(solicitud, clave, reclamo.get(), exito, operacion);
            }
            Optional<RegistroClave> existente = registro.buscar(solicitud.usuarioId(), clave);
            if (existente.isPresent()) {
                return resolver(existente.get(), huella);
            }
        }
        throw new SolicitudEnProcesoException(segundosReintento);
    }

    private <T> RespuestaIdempotente ejecutarReclamado(SolicitudIdempotente solicitud, UUID clave, UUID reclamo,
            HttpStatus exito, Supplier<T> operacion) {
        String cuerpo;
        try {
            cuerpo = serializar(operacion.get());
        } catch (RuntimeException error) {
            liberar(solicitud, clave, reclamo, error);
            throw error;
        }
        cerrar(solicitud, clave, reclamo, exito, cuerpo);
        return new RespuestaIdempotente(exito.value(), cuerpo, false);
    }

    // La operación ya tuvo efecto: un fallo al cerrar no puede ocultar el éxito ni ejecutarla de nuevo. La
    // clave queda EN_PROCESO hasta que venza el lease y, si el usuario reintenta, choca con su propia reserva
    // (brecha D6, documentada en ADR-007).
    private void cerrar(SolicitudIdempotente solicitud, UUID clave, UUID reclamo, HttpStatus exito, String cuerpo) {
        try {
            if (!registro.completar(solicitud.usuarioId(), clave, reclamo, exito.value(), cuerpo, vigencia)) {
                LOG.error("No se pudo completar la clave de idempotencia {} del usuario {}: el reclamo ya no es "
                        + "el vigente.", clave, solicitud.usuarioId());
            }
        } catch (RuntimeException error) {
            LOG.error("Falló el cierre de la clave de idempotencia {} del usuario {}; la operación ya se ejecutó.",
                    clave, solicitud.usuarioId(), error);
        }
    }

    private void liberar(SolicitudIdempotente solicitud, UUID clave, UUID reclamo, RuntimeException original) {
        try {
            registro.liberar(solicitud.usuarioId(), clave, reclamo);
        } catch (RuntimeException error) {
            original.addSuppressed(error);
        }
    }

    // La huella distinta se evalúa antes que el estado: es un error del cliente, no una espera (D12).
    private RespuestaIdempotente resolver(RegistroClave existente, String huella) {
        if (!existente.huella().equals(huella)) {
            throw new ClaveIdempotenciaReutilizadaException();
        }
        if (existente.estado() == RegistroClave.Estado.COMPLETADA) {
            return new RespuestaIdempotente(existente.codigoEstado(), existente.cuerpo(), true);
        }
        throw new SolicitudEnProcesoException(segundosReintento);
    }

    private static UUID validar(String clave) {
        if (clave == null || !UUID_CANONICO.matcher(clave).matches()) {
            throw new ClaveIdempotenciaInvalidaException();
        }
        return UUID.fromString(clave);
    }

    static String huella(String operacion, String contenidoCanonico) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] resumen = sha.digest((operacion + "\n" + contenidoCanonico).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", error);
        }
    }

    private String serializar(Object resultado) {
        try {
            return mapeador.writeValueAsString(resultado);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo serializar la respuesta de la operación.", error);
        }
    }
}
