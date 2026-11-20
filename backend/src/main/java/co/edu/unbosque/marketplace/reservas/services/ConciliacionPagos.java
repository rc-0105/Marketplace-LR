package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.reservas.model.EstadoPago;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Conciliación de pagos (SOF-34, S8; ADR-4). Un pago en ERROR es uno cuyo resultado no se conoce: la pasarela
 * no respondió a tiempo, pero pudo cobrar igual, o un reembolso inmediato falló. Cada corrida le pregunta a la
 * pasarela si hay un cobro aprobado de esa reserva:
 *
 * <ul>
 *   <li>si lo hay y la reserva sigue PENDIENTE, la confirma con esa transacción;</li>
 *   <li>si lo hay y la reserva ya no está PENDIENTE (venció o se canceló), lo reembolsa: REEMBOLSADO;</li>
 *   <li>si no lo hay y ya pasó el apartado más {@code pagos.conciliacion.gracia-minutos}, lo cierra como
 *       NO_COBRADO; antes de eso espera, porque la búsqueda de la pasarela puede tardar en reflejar un cobro.</li>
 * </ul>
 *
 * <p>Si la pasarela no responde o el reembolso falla, la reserva sigue en ERROR y se reintenta en la próxima
 * corrida. Las escrituras son las actualizaciones condicionales de {@link RegistroReservas}, así que un
 * reintento simultáneo del huésped no se pisa con la conciliación.
 */
@Service
public class ConciliacionPagos {

    private static final Logger LOG = LoggerFactory.getLogger(ConciliacionPagos.class);
    private static final String ENTIDAD = "Reserva";

    private final ReservaRepository reservas;
    private final RegistroReservas registro;
    private final PasarelaPagos pasarela;
    private final PublicadorEventos publicador;
    private final Clock reloj;
    private final Duration gracia;

    public ConciliacionPagos(ReservaRepository reservas, RegistroReservas registro, PasarelaPagos pasarela,
            PublicadorEventos publicador, Clock reloj,
            @Value("${pagos.conciliacion.gracia-minutos:60}") long minutosGracia) {
        this.reservas = reservas;
        this.registro = registro;
        this.pasarela = pasarela;
        this.publicador = publicador;
        this.reloj = reloj;
        this.gracia = Duration.ofMinutes(minutosGracia);
    }

    /** Resuelve cada pago en ERROR; una falla con una reserva no detiene las demás. */
    @Scheduled(initialDelayString = "${pagos.conciliacion.intervalo-minutos:5}",
            fixedDelayString = "${pagos.conciliacion.intervalo-minutos:5}", timeUnit = TimeUnit.MINUTES)
    public void conciliar() {
        for (Reserva reserva : reservas.findByEstadoPagoOrderById(EstadoPago.ERROR)) {
            try {
                conciliar(reserva);
            } catch (FallaPasarelaException falla) {
                LOG.warn("No se pudo conciliar el pago de la reserva {}; se reintenta en la próxima corrida: {}",
                        reserva.getId(), falla.getMessage());
            } catch (RuntimeException error) {
                // Un error inesperado con una reserva (p. ej. de la base de datos) no debe frenar a las siguientes.
                LOG.error("Error inesperado al conciliar el pago de la reserva {}", reserva.getId(), error);
            }
        }
    }

    private void conciliar(Reserva reserva) {
        Long id = reserva.getId();
        Optional<String> cobro = pasarela.buscarCobroAprobado(reserva.referenciaPago());
        if (cobro.isEmpty()) {
            if (Instant.now(reloj).isAfter(reserva.getVenceEn().plus(gracia))) {
                registro.marcarNoCobrado(id);
                LOG.info("El pago en error de la reserva {} nunca se cobró en la pasarela", id);
                auditar(reserva, ResultadoEvento.FALLO, Map.of("resultado", "no_cobrado"));
            }
            return;
        }
        String transaccion = cobro.get();
        if (registro.confirmarPago(id, transaccion)) {
            LOG.info("La conciliación confirmó la reserva {} con la transacción {}", id, transaccion);
            auditar(reserva, ResultadoEvento.EXITO, Map.of("resultado", "confirmado", "transaccionId", transaccion));
            return;
        }
        Optional<Reserva> actual = reservas.findById(id);
        if (actual.isPresent() && transaccion.equals(actual.get().getTransaccionId())) {
            return;
        }
        // La pasarela cobró, pero la reserva ya no estaba PENDIENTE: el cobro se devuelve. Si el reembolso falla,
        // la excepción deja la reserva en ERROR para la próxima corrida.
        pasarela.reembolsar(transaccion);
        registro.marcarReembolsado(id, transaccion);
        LOG.warn("La conciliación reembolsó la transacción {}: la reserva {} ya no estaba pendiente", transaccion, id);
        auditar(reserva, ResultadoEvento.FALLO, Map.of("resultado", "reembolsado", "transaccionId", transaccion));
    }

    private void auditar(Reserva reserva, ResultadoEvento resultado, Map<String, Object> datos) {
        Map<String, Object> contexto = new LinkedHashMap<>(datos);
        contexto.put("operacion", "conciliar_pago");
        publicador.publicar(new EventoCritico(String.valueOf(reserva.getHuespedId()), AccionCritica.RESERVA, ENTIDAD,
                String.valueOf(reserva.getId()), resultado, Instant.now(reloj), contexto));
    }
}
