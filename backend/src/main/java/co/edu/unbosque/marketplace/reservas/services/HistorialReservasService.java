package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.alojamientos.services.AlojamientoReservado;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Estado e historial de las reservas del huésped (SOF-35, RF-38). Solo lee: el estado que muestra es el actual
 * ({@link Reserva#estadoActual}), sin esperar a que la tarea programada venza los apartados agotados.
 */
@Service
public class HistorialReservasService {

    // "Hoy" es el día en Colombia, donde están los alojamientos, no el del servidor.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final ReservaRepository reservas;
    private final PublicacionService publicaciones;
    private final Clock reloj;

    public HistorialReservasService(ReservaRepository reservas, PublicacionService publicaciones, Clock reloj) {
        this.reservas = reservas;
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    /**
     * Solo las reservas del huésped dado. Una reserva es futura hasta el día de su salida inclusive: quien
     * todavía está alojado, o sale hoy, sigue viéndola entre las próximas.
     */
    public HistorialReservas mias(Long huespedId) {
        List<Reserva> propias = reservas.findByHuespedIdOrderByLlegadaAscIdAsc(huespedId);
        Map<Long, AlojamientoReservado> alojamientos = publicaciones.consultarReservados(
                propias.stream().map(Reserva::getPublicacionId).collect(Collectors.toSet()));
        Instant ahora = Instant.now(reloj);
        LocalDate hoy = LocalDate.ofInstant(ahora, ZONA);

        List<ReservaConsultada> futuras = new ArrayList<>();
        List<ReservaConsultada> pasadas = new ArrayList<>();
        for (Reserva reserva : propias) {
            ReservaConsultada consultada = new ReservaConsultada(reserva, reserva.estadoActual(ahora),
                    alojamientos.get(reserva.getPublicacionId()));
            (reserva.getSalida().isBefore(hoy) ? pasadas : futuras).add(consultada);
        }
        Collections.reverse(pasadas);
        return new HistorialReservas(List.copyOf(futuras), List.copyOf(pasadas));
    }
}
