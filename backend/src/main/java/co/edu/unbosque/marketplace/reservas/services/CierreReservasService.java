package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.compartido.eventos.AccionCritica;
import co.edu.unbosque.marketplace.compartido.eventos.EventoCritico;
import co.edu.unbosque.marketplace.compartido.eventos.PublicadorEventos;
import co.edu.unbosque.marketplace.compartido.eventos.ResultadoEvento;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierre de reservas con la estancia terminada (SOF-78, HU-33, RF-50): una CONFIRMADA cuya fecha de salida
 * ya pasó queda COMPLETADA, lo que habilita la reseña (otro épica, todavía sin empezar) y mantiene los
 * indicadores correctos. Nunca toca una reserva que no esté CONFIRMADA: una CANCELADA sigue CANCELADA
 * aunque su fecha de salida también haya pasado.
 *
 * <p>Es un proceso programado sin endpoint público, al estilo de {@link ConciliacionPagos}: la actualización
 * es un lote condicional en {@link ReservaRepository}, así que no compite con una lectura de disponibilidad
 * ni con otra escritura sobre la misma reserva.
 */
@Service
public class CierreReservasService {

    private static final Logger LOG = LoggerFactory.getLogger(CierreReservasService.class);
    private static final String ENTIDAD = "Reserva";
    // "Hoy" es el día en Colombia, donde están los alojamientos, no el del servidor.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final ReservaRepository reservas;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public CierreReservasService(ReservaRepository reservas, PublicadorEventos publicador, Clock reloj) {
        this.reservas = reservas;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    /**
     * Cada {@code marketplace.reservas.cierre.intervalo-horas} horas (24 por defecto, una vez al día).
     * Transaccional porque la actualización en lote de {@link ReservaRepository} es un {@code @Modifying}
     * que necesita una transacción activa para hacer flush, igual que las demás escrituras de reservas.
     */
    @Transactional
    @Scheduled(initialDelayString = "${marketplace.reservas.cierre.intervalo-horas:24}",
            fixedDelayString = "${marketplace.reservas.cierre.intervalo-horas:24}", timeUnit = TimeUnit.HOURS)
    public void cerrarReservasConEstanciaTerminada() {
        int cerradas = reservas.completarConEstanciaTerminada(LocalDate.now(reloj.withZone(ZONA)));
        if (cerradas == 0) {
            return;
        }
        LOG.info("El cierre de reservas completó {} reserva(s) con la estancia terminada", cerradas);
        publicador.publicar(new EventoCritico(null, AccionCritica.RESERVA, ENTIDAD, null, ResultadoEvento.EXITO,
                Instant.now(reloj), Map.of("operacion", "cierre_reservas", "cerradas", cerradas)));
    }
}
