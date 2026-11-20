package co.edu.unbosque.marketplace.reservas.repository;

import co.edu.unbosque.marketplace.reservas.model.Reserva;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Una reserva ocupa noches si está CONFIRMADA o si está PENDIENTE con el apartado vigente
 * ({@code venceEn > ahora}, ADR-4). Todas las consultas de lectura aplican esa regla con el instante
 * {@code ahora} que da el reloj de la aplicación, de modo que un apartado agotado deja de contar sin
 * esperar a que la tarea programada lo pase a VENCIDA. Las ventanas son semiabiertas
 * {@code [desde, hasta)}: {@code llegada < hasta} y {@code salida > desde}, así que una reserva que solo
 * toca el borde no cuenta.
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    String OCUPA = """
            (r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA
             or (r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.PENDIENTE
                 and r.venceEn > :ahora))""";

    // Reintento idempotente (ADR-4): reservas vigentes del mismo huésped con las mismas fechas exactas.
    @Query("select r from Reserva r where r.huespedId = :huespedId and r.publicacionId = :publicacionId"
            + " and r.llegada = :llegada and r.salida = :salida and " + OCUPA + " order by r.id")
    List<Reserva> findVigentesIdenticas(@Param("huespedId") Long huespedId,
            @Param("publicacionId") Long publicacionId, @Param("llegada") LocalDate llegada,
            @Param("salida") LocalDate salida, @Param("ahora") Instant ahora);

    // Reservas que ocupan noches de la publicación dentro de la ventana [desde, hasta).
    @Query("select r from Reserva r where r.publicacionId = :publicacionId and r.llegada < :hasta"
            + " and r.salida > :desde and " + OCUPA + " order by r.id")
    List<Reserva> findOcupantes(@Param("publicacionId") Long publicacionId, @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta, @Param("ahora") Instant ahora);

    // Misma regla, pero para varias publicaciones a la vez: una sola consulta por lote.
    @Query("select r from Reserva r where r.publicacionId in :publicacionIds and r.llegada < :hasta"
            + " and r.salida > :desde and " + OCUPA)
    List<Reserva> findOcupantesDeVarias(@Param("publicacionIds") Collection<Long> publicacionIds,
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, @Param("ahora") Instant ahora);

    // Pasa a VENCIDA los apartados agotados de la publicación que cruzan el rango, para que la restricción de
    // exclusión (que no puede usar now()) los suelte antes de insertar otra reserva.
    @Modifying(flushAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.VENCIDA"
            + " where r.publicacionId = :publicacionId and r.llegada < :hasta and r.salida > :desde"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.PENDIENTE"
            + " and r.venceEn <= :ahora")
    int vencerCaducadosEn(@Param("publicacionId") Long publicacionId, @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta, @Param("ahora") Instant ahora);

    // Igual, para todas las publicaciones: lo usa la tarea programada.
    @Modifying(flushAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.VENCIDA"
            + " where r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.PENDIENTE"
            + " and r.venceEn <= :ahora")
    int vencerCaducados(@Param("ahora") Instant ahora);
}
