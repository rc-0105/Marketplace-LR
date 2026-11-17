package co.edu.unbosque.marketplace.compartido.consultas;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto con el que {@code disponibilidad} pregunta qué días ocupan las reservas de una publicación para
 * armar su calendario, sin conocer el modelo de reservas (ADR-2).
 *
 * <p>Contrato para HU-22: la implementación real MUST ser un {@code @Service} o {@code @Component}
 * escaneado, como la de {@link ConsultaReservasFuturas}, y MUST devolver solo reservas CONFIRMADAS. Las
 * pendientes, canceladas o de cualquier otro estado no ocupan días: {@code disponibilidad} no reinterpreta
 * estados, cada reserva que el puerto devuelve queda ocupada. Mientras no exista, {@code
 * ConfiguracionReservasPorDefecto} registra un bean que no devuelve reservas.
 */
public interface ConsultaReservasCalendario {

    /**
     * Reservas confirmadas de la publicación que se cruzan con la ventana semiabierta {@code [desde, hasta)},
     * sin orden garantizado.
     */
    List<ReservaOcupada> confirmadasEntre(Long publicacionId, LocalDate desde, LocalDate hasta);
}
