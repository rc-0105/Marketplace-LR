package co.edu.unbosque.marketplace.reservas.services;

import co.edu.unbosque.marketplace.reservas.model.Cancelacion;
import co.edu.unbosque.marketplace.reservas.model.Reserva;
import co.edu.unbosque.marketplace.reservas.repository.CancelacionRepository;
import co.edu.unbosque.marketplace.reservas.repository.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
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
}
