package co.edu.unbosque.marketplace.reservas.repository;

import co.edu.unbosque.marketplace.reservas.model.EstadoPago;
import co.edu.unbosque.marketplace.reservas.model.EstadoReserva;
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
 * Una reserva ocupa noches si está CONFIRMADA, EN_REVISION_MANUAL (SOF-83) o PENDIENTE con el apartado vigente
 * ({@code venceEn > ahora}, ADR-4). Todas las consultas de lectura aplican esa regla con el instante
 * {@code ahora} que da el reloj de la aplicación, de modo que un apartado agotado deja de contar sin
 * esperar a que la tarea programada lo pase a VENCIDA. Las ventanas son semiabiertas
 * {@code [desde, hasta)}: {@code llegada < hasta} y {@code salida > desde}, así que una reserva que solo
 * toca el borde no cuenta.
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    String OCUPA = """
            (r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA
             or r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.EN_REVISION_MANUAL
             or (r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.PENDIENTE
                 and r.venceEn > :ahora))""";

    // Historial del huésped (SOF-35): todas sus reservas, en cualquier estado, por fecha de llegada.
    List<Reserva> findByHuespedIdOrderByLlegadaAscIdAsc(Long huespedId);

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

    // Pago aprobado (SOF-34): confirma solo si sigue PENDIENTE. Devuelve 0 si el apartado ya venció o si otro
    // intento la resolvió primero; quien llama decide qué hacer con el cobro que quedó sin reserva.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA,"
            + " r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.APROBADO,"
            + " r.transaccionId = :transaccionId where r.id = :id"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.PENDIENTE")
    int confirmarPago(@Param("id") Long id, @Param("transaccionId") String transaccionId);

    // Pago rechazado: la PENDIENTE pasa a CANCELADA, lo que libera sus noches en la restricción de exclusión.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CANCELADA,"
            + " r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.RECHAZADO where r.id = :id"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.PENDIENTE")
    int rechazarPago(@Param("id") Long id);

    // La pasarela no respondió, falló, o un reembolso no se completó: el resultado del cobro no se conoce y la
    // conciliación debe resolverlo. Se marca aunque la reserva ya no esté PENDIENTE (el apartado pudo vencer
    // mientras se esperaba a la pasarela); solo se respeta un pago ya resuelto.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.ERROR"
            + " where r.id = :id"
            + " and r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.SIN_PAGO")
    int registrarErrorPago(@Param("id") Long id);

    // Conciliación (SOF-34): los pagos que quedaron en ERROR, en el orden en que se crearon las reservas.
    List<Reserva> findByEstadoPagoOrderById(EstadoPago estadoPago);

    // La pasarela cobró una reserva que ya no estaba PENDIENTE y el cobro se devolvió.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.REEMBOLSADO,"
            + " r.transaccionId = :transaccionId where r.id = :id"
            + " and r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.ERROR"
            + " and r.estado <> co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA")
    int marcarReembolsado(@Param("id") Long id, @Param("transaccionId") String transaccionId);

    // La pasarela nunca cobró ese pago en ERROR.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.NO_COBRADO"
            + " where r.id = :id"
            + " and r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.ERROR"
            + " and r.estado <> co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA")
    int marcarNoCobrado(@Param("id") Long id);

    // Cancelación (SOF-75): cancela solo si sigue CONFIRMADA. Devuelve 0 si ya no lo estaba (otra
    // cancelación, o la carrera con una conciliación que la resolvió primero); quien llama decide qué hacer.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CANCELADA"
            + " where r.id = :id"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA")
    int cancelar(@Param("id") Long id);

    // Bloqueo de la publicación (SOF-83, AC2): sus reservas CONFIRMADA cuya salida es posterior a hoy pasan a
    // revisión manual. Una estancia ya terminada no cambia. Devuelve cuántas pasaron. No limpia el contexto de
    // persistencia: corre dentro de la transacción del bloqueo, que todavía arma la respuesta con la publicación.
    @Modifying(flushAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.EN_REVISION_MANUAL"
            + " where r.publicacionId = :publicacionId and r.salida > :hoy"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA")
    int enviarARevision(@Param("publicacionId") Long publicacionId, @Param("hoy") LocalDate hoy);

    // Revisión manual (SOF-83): las reservas que esperan la decisión del administrador, por fecha de llegada.
    List<Reserva> findByEstadoOrderByLlegadaAscIdAsc(EstadoReserva estado);

    // El administrador la confirma: vuelve a CONFIRMADA solo si sigue en revisión. Devuelve 0 si otra
    // resolución la cambió primero.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CONFIRMADA"
            + " where r.id = :id"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.EN_REVISION_MANUAL")
    int confirmarRevision(@Param("id") Long id);

    // El administrador la cancela y la pasarela ya devolvió el cobro: CANCELADA con el pago REEMBOLSADO, solo
    // si sigue en revisión. La transacción se conserva, como exige reserva_transaccion_check.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CANCELADA,"
            + " r.estadoPago = co.edu.unbosque.marketplace.reservas.model.EstadoPago.REEMBOLSADO where r.id = :id"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.EN_REVISION_MANUAL"
            + " and r.transaccionId is not null")
    int cancelarRevisionReembolsada(@Param("id") Long id);

    // Igual, para una reserva sin cobro registrado en la pasarela: el estado del pago no cambia.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reserva r set r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.CANCELADA"
            + " where r.id = :id"
            + " and r.estado = co.edu.unbosque.marketplace.reservas.model.EstadoReserva.EN_REVISION_MANUAL")
    int cancelarRevision(@Param("id") Long id);
}
