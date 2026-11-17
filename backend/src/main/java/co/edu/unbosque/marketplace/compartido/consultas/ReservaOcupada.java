package co.edu.unbosque.marketplace.compartido.consultas;

import java.time.LocalDate;

/** Reserva confirmada que ocupa las noches del rango semiabierto {@code [llegada, salida)}. */
public record ReservaOcupada(Long reservaId, LocalDate llegada, LocalDate salida) {
}
