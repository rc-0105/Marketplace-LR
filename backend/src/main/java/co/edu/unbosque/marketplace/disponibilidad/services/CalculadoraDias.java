package co.edu.unbosque.marketplace.disponibilidad.services;

import co.edu.unbosque.marketplace.compartido.consultas.ReservaOcupada;
import co.edu.unbosque.marketplace.disponibilidad.model.DiaCalendario;
import co.edu.unbosque.marketplace.disponibilidad.model.EstadoDia;
import co.edu.unbosque.marketplace.disponibilidad.model.PeriodoDisponible;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Estado de cada día de la ventana semiabierta {@code [desde, hasta)} (RF-25). Es una función pura: no usa
 * Spring, ni el reloj, ni el repositorio.
 *
 * <p>Parte con todos los días BLOQUEADOS, marca como DISPONIBLES los que cubre algún periodo y, al final,
 * como RESERVADOS los que ocupa alguna reserva, así que la reserva gana aunque caiga fuera de cualquier periodo.
 * Cada pasada recorta el rango a la ventana y toca a lo sumo una celda por día: O(n + p + r).
 */
final class CalculadoraDias {

    private CalculadoraDias() {
    }

    static List<DiaCalendario> calcular(LocalDate desde, LocalDate hasta, List<PeriodoDisponible> periodos,
            List<ReservaOcupada> reservas, boolean conReservaId) {
        int total = (int) Math.max(0, ChronoUnit.DAYS.between(desde, hasta));
        EstadoDia[] estados = new EstadoDia[total];
        Arrays.fill(estados, EstadoDia.BLOQUEADO);
        Long[] reservaIds = new Long[total];

        for (PeriodoDisponible periodo : periodos) {
            marcar(estados, reservaIds, desde, periodo.getFechaInicio(), periodo.getFechaFin(), EstadoDia.DISPONIBLE,
                    null);
        }
        for (ReservaOcupada reserva : reservas) {
            marcar(estados, reservaIds, desde, reserva.llegada(), reserva.salida(), EstadoDia.RESERVADO,
                    conReservaId ? reserva.reservaId() : null);
        }

        List<DiaCalendario> dias = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            dias.add(new DiaCalendario(desde.plusDays(i), estados[i], reservaIds[i]));
        }
        return dias;
    }

    // Marca los días de [inicio, fin) que caen dentro de la ventana, que tiene estados.length días desde `desde`.
    private static void marcar(EstadoDia[] estados, Long[] reservaIds, LocalDate desde, LocalDate inicio,
            LocalDate fin, EstadoDia estado, Long reservaId) {
        long primero = Math.max(0, ChronoUnit.DAYS.between(desde, inicio));
        long ultimoExclusivo = Math.min(estados.length, ChronoUnit.DAYS.between(desde, fin));
        for (long i = primero; i < ultimoExclusivo; i++) {
            estados[(int) i] = estado;
            reservaIds[(int) i] = reservaId;
        }
    }
}
