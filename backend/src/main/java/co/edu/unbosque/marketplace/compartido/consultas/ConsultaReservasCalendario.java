package co.edu.unbosque.marketplace.compartido.consultas;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto con el que {@code disponibilidad} pregunta qué noches ocupan las reservas de una publicación, para
 * armar su calendario y decidir si una estancia está libre, sin conocer el modelo de reservas (ADR-2).
 *
 * <p>Contrato para SOF-33: la implementación real MUST ser un {@code @Service} o {@code @Component}
 * escaneado, como la de {@link ConsultaReservasFuturas}, y MUST devolver las reservas que OCUPAN noches:
 * las CONFIRMADAS y las PENDIENTES dentro de su tiempo de apartado (ADR-4). Qué estados ocupan lo decide
 * {@code reservas}; {@code disponibilidad} no reinterpreta estados y cada reserva que el puerto devuelve
 * queda ocupada. Liberar una noche es que el puerto deje de devolver la reserva (cancelada o pendiente
 * vencida): {@code disponibilidad} no guarda ningún estado de bloqueo. Mientras no exista, {@code
 * ConfiguracionReservasPorDefecto} registra un bean que no devuelve reservas.
 */
public interface ConsultaReservasCalendario {

    /**
     * Reservas que ocupan noches de la publicación y se cruzan con la ventana semiabierta {@code [desde,
     * hasta)}, sin orden garantizado.
     */
    List<ReservaOcupada> ocupantesEntre(Long publicacionId, LocalDate desde, LocalDate hasta);
}
