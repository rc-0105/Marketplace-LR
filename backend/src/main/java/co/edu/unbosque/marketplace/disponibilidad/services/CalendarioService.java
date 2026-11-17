package co.edu.unbosque.marketplace.disponibilidad.services;

import co.edu.unbosque.marketplace.alojamientos.services.AccesoPublicacion;
import co.edu.unbosque.marketplace.alojamientos.services.PublicacionService;
import co.edu.unbosque.marketplace.compartido.consultas.ConsultaReservasCalendario;
import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.CalendarioNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.exceptions.RangoCalendarioInvalidoException;
import co.edu.unbosque.marketplace.disponibilidad.model.Calendario;
import co.edu.unbosque.marketplace.disponibilidad.model.DiaCalendario;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.repository.PeriodoDisponibleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendario por día de una publicación (RF-25): solo lectura. Orquesta el acceso, la ventana, los
 * periodos y las reservas, y delega el cálculo en {@link CalculadoraDias}.
 */
@Service
public class CalendarioService {

    // "Hoy" es el día en Colombia, donde están los alojamientos, no el del servidor.
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int MESES_POR_DEFECTO = 12;
    private static final long DIAS_MAXIMOS = 366;

    private final PeriodoDisponibleRepository periodos;
    private final PublicacionService publicaciones;
    private final ConsultaReservasCalendario reservas;
    private final Clock reloj;

    public CalendarioService(PeriodoDisponibleRepository periodos, PublicacionService publicaciones,
            ConsultaReservasCalendario reservas, Clock reloj) {
        this.periodos = periodos;
        this.publicaciones = publicaciones;
        this.reservas = reservas;
        this.reloj = reloj;
    }

    /**
     * Calendario de la ventana semiabierta {@code [desde, hasta)}; un extremo nulo toma su valor por
     * defecto. Una publicación inexistente, o no ACTIVA para quien no es su dueño, responde lo mismo para
     * no revelar que existe. Solo el dueño recibe el id de cada reserva.
     */
    @Transactional(readOnly = true)
    public Calendario consultar(Long publicacionId, Long usuarioId, LocalDate desde, LocalDate hasta) {
        // El acceso va antes que el rango: a quien no puede ver la publicación no se le dice nada más.
        AccesoPublicacion acceso = publicaciones.consultarAcceso(publicacionId)
                .filter(a -> a.esDe(usuarioId) || a.publica()).orElseThrow(CalendarioNoEncontradoException::new);
        LocalDate inicio = desde != null ? desde : LocalDate.now(reloj.withZone(ZONA));
        LocalDate fin = hasta != null ? hasta : inicio.plusMonths(MESES_POR_DEFECTO);
        validarVentana(inicio, fin);

        List<PeriodoDisponible> periodosDeLaVentana = periodos
                .findByPublicacionIdAndFechaInicioLessThanAndFechaFinGreaterThan(publicacionId, fin, inicio);
        List<ReservaOcupada> reservasDeLaVentana = reservas.confirmadasEntre(publicacionId, inicio, fin);
        List<DiaCalendario> dias = CalculadoraDias.calcular(inicio, fin, periodosDeLaVentana, reservasDeLaVentana,
                acceso.esDe(usuarioId));
        return new Calendario(publicacionId, inicio, fin, periodos.existsByPublicacionId(publicacionId), dias);
    }

    private static void validarVentana(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new RangoCalendarioInvalidoException("La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMOS) {
            throw new RangoCalendarioInvalidoException(
                    "El calendario no puede abarcar más de " + DIAS_MAXIMOS + " días.");
        }
    }
}
