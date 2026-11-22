package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.CancelacionRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrituras transaccionales de reservas (SOF-33). Vive aparte de {@link ReservaService} para que cada
 * escritura sea su propia transacción: si la inserción viola la restricción de exclusión, PostgreSQL aborta
 * esa transacción y {@code ReservaService}, que no está dentro de ninguna, todavía puede consultar de nuevo.
 */
@Service
public class RegistroReservas {

    private final ReservaRepository reservas;
    private final CancelacionRepository cancelaciones;
    private final Clock reloj;

    public RegistroReservas(ReservaRepository reservas, CancelacionRepository cancelaciones, Clock reloj) {
        this.reservas = reservas;
        this.cancelaciones = cancelaciones;
        this.reloj = reloj;
    }

    /**
     * Inserta la reserva. Antes pasa a VENCIDA los apartados agotados de la publicación que cruzan las mismas
     * noches: la restricción de exclusión no puede usar {@code now()} y, sin esto, un apartado ya vencido
     * seguiría bloqueando a otro huésped hasta la próxima corrida de la tarea programada. El flush evalúa
     * la restricción aquí y no al confirmar la transacción.
     */
    @Transactional
    public Reserva registrar(Reserva nueva) {
        reservas.vencerCaducadosEn(nueva.getPublicacionId(), nueva.getLlegada(), nueva.getSalida(),
                Instant.now(reloj));
        return reservas.saveAndFlush(nueva);
    }

    /**
     * Antes de insertar (SOF-79): vence, fila por fila, las solicitudes agotadas que cruzan el rango y devuelve solo
     * las que esta llamada cambió, para que quien llama avise a su huésped exactamente una vez, y solo después de
     * que esta transacción terminó. Cada vencimiento es su propia actualización condicional, igual que en la tarea
     * programada: quien pierde la carrera no devuelve la fila.
     */
    @Transactional
    public List<Reserva> vencerSolicitudesCaducadasEn(Long publicacionId, LocalDate desde, LocalDate hasta) {
        return reservas.findSolicitudesCaducadasEn(publicacionId, desde, hasta, Instant.now(reloj)).stream()
                .filter(candidata -> reservas.vencerSolicitud(candidata.getId(), Instant.now(reloj)) == 1).toList();
    }

    /** Pago aprobado (SOF-34): confirma si la reserva sigue PENDIENTE; {@code false} si ya no lo estaba. */
    @Transactional
    public boolean confirmarPago(Long reservaId, String transaccionId) {
        return reservas.confirmarPago(reservaId, transaccionId) == 1;
    }

    /** Pago rechazado: la PENDIENTE pasa a CANCELADA y libera sus noches; {@code false} si ya no lo estaba. */
    @Transactional
    public boolean rechazarPago(Long reservaId) {
        return reservas.rechazarPago(reservaId) == 1;
    }

    /** El resultado del cobro no se conoce: el pago pasa a ERROR para que la conciliación lo resuelva. */
    @Transactional
    public void registrarErrorPago(Long reservaId) {
        reservas.registrarErrorPago(reservaId);
    }

    /** Conciliación: el cobro de una reserva que ya no estaba PENDIENTE se devolvió. */
    @Transactional
    public void marcarReembolsado(Long reservaId, String transaccionId) {
        reservas.marcarReembolsado(reservaId, transaccionId);
    }

    /** Conciliación: la pasarela nunca cobró ese pago en ERROR. */
    @Transactional
    public void marcarNoCobrado(Long reservaId) {
        reservas.marcarNoCobrado(reservaId);
    }


    /** Pasa a VENCIDA todos los apartados agotados y devuelve cuántos fueron (tarea programada). */
    @Transactional
    public int vencerCaducados() {
        return reservas.vencerCaducados(Instant.now(reloj));
    }

    /**
     * Cancelación (SOF-75): si la reserva sigue CONFIRMADA la cancela y registra {@code cancelacion} en la
     * misma transacción, así nunca queda una reserva CANCELADA sin su registro (AC2). Si el registro falla,
     * el cambio de estado se revierte. Devuelve {@code false}, sin registrar nada, si ya no estaba CONFIRMADA.
     */
    @Transactional
    public boolean cancelar(Cancelacion cancelacion) {
        if (reservas.cancelar(cancelacion.getReservaId()) != 1) {
            return false;
        }
        cancelaciones.saveAndFlush(cancelacion);
        return true;
    }

    /**
     * Cancelación del huésped de su solicitud pendiente de aprobación (SOF-79): igual que {@link #cancelar} pero
     * sobre PENDIENTE_APROBACION; {@code false}, sin registrar nada, si ya no lo estaba.
     */
    @Transactional
    public boolean cancelarSolicitud(Cancelacion cancelacion) {
        if (reservas.cancelarSolicitud(cancelacion.getReservaId()) != 1) {
            return false;
        }
        cancelaciones.saveAndFlush(cancelacion);
        return true;
    }

    /**
     * Aprobación del anfitrión (SOF-79): la solicitud pasa a PENDIENTE con el apartado {@code venceEn} solo si sigue
     * esperando y su ventana no se agotó; {@code false} si ya no (otra respuesta, o el vencimiento, ganó).
     */
    @Transactional
    public boolean aprobarSolicitud(Long reservaId, Instant venceEn) {
        return reservas.aprobarSolicitud(reservaId, Instant.now(reloj), venceEn) == 1;
    }

    /** Rechazo del anfitrión (SOF-79): RECHAZADA con motivo, en las mismas condiciones que {@link #aprobarSolicitud}. */
    @Transactional
    public boolean rechazarSolicitud(Long reservaId, String motivo) {
        return reservas.rechazarSolicitud(reservaId, Instant.now(reloj), motivo) == 1;
    }

    /** Vence una solicitud cuya ventana se agotó (SOF-79); {@code true} solo para quien cambió la fila. */
    @Transactional
    public boolean vencerSolicitud(Long reservaId) {
        return reservas.vencerSolicitud(reservaId, Instant.now(reloj)) == 1;
    }

    /** Revisión manual (SOF-83): la reserva vuelve a CONFIRMADA si sigue en revisión; {@code false} si no. */
    @Transactional
    public boolean confirmarRevision(Long reservaId) {
        return reservas.confirmarRevision(reservaId) == 1;
    }

    /**
     * Revisión manual (SOF-83): si la reserva sigue en revisión la cancela, con el pago REEMBOLSADO cuando la
     * pasarela ya devolvió el cobro, y registra {@code cancelacion} en la misma transacción, igual que
     * {@link #cancelar}. Devuelve {@code false}, sin registrar nada, si ya no estaba en revisión.
     */
    @Transactional
    public boolean cancelarRevision(Cancelacion cancelacion, boolean reembolsado) {
        Long reservaId = cancelacion.getReservaId();
        int cambiadas = reembolsado ? reservas.cancelarRevisionReembolsada(reservaId)
                : reservas.cancelarRevision(reservaId);
        if (cambiadas != 1) {
            return false;
        }
        cancelaciones.saveAndFlush(cancelacion);
        return true;
    }

    @Transactional
    public void guardarResultado(Cancelacion cancelacion) {
        cancelaciones.save(cancelacion);
    }
}
