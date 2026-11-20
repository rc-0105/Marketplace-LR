package co.edu.unbosque.marketplace.compartido.consultas;

import java.time.LocalDate;

/** Reserva que ocupa las noches del rango semiabierto {@code [llegada, salida)}. */
public record ReservaOcupada(Long reservaId, LocalDate llegada, LocalDate salida) {

    /** Ocupa alguna noche de {@code [desde, hasta)}: tocar el borde (salir el día que otro llega) no cruza. */
    public boolean cruza(LocalDate desde, LocalDate hasta) {
        return llegada.isBefore(hasta) && salida.isAfter(desde);
    }
}
