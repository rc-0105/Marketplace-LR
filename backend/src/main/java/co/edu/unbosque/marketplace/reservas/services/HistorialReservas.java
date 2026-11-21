package co.edu.unbosque.marketplace.reservas.services;

import java.util.List;

/**
 * Reservas de un huésped (SOF-35): las futuras, de la llegada más próxima a la más lejana, y las pasadas, de
 * la más reciente a la más antigua.
 */
public record HistorialReservas(List<ReservaConsultada> futuras, List<ReservaConsultada> pasadas) {
}
