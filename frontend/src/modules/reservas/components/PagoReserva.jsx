import { useState } from 'react';
import PropTypes from 'prop-types';
import { cancelarReserva, pagarReserva } from '../services/reservaService.js';
import estilos from './PagoReserva.module.css';

const MENSAJE_ERROR = 'No pudimos procesar el pago. Intenta de nuevo.';
const MENSAJE_ERROR_CANCELACION = 'No pudimos cancelar la reserva. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

// Tarjetas de prueba de Stripe: ninguna hace un cobro real (SOF-34).
const TARJETAS = [
  { valor: 'pm_card_visa', etiqueta: 'Visa de prueba (aprobada)' },
  { valor: 'pm_card_mastercard', etiqueta: 'Mastercard de prueba (aprobada)' },
  { valor: 'pm_card_chargeDeclined', etiqueta: 'Tarjeta de prueba rechazada' },
  { valor: 'pm_card_chargeDeclinedInsufficientFunds', etiqueta: 'Tarjeta de prueba sin fondos' },
];

/**
 * Pago de una reserva PENDIENTE con la pasarela en modo prueba (SOF-34). Aprobado o rechazado, el
 * resultado es definitivo y el botón desaparece; si la pasarela no respondió o falló (5xx), la reserva
 * sigue apartada y se puede reintentar.
 */
export default function PagoReserva({ reservaId, total }) {
  const [tarjeta, setTarjeta] = useState(TARJETAS[0].valor);
  const [claveIntento, setClaveIntento] = useState(() => crypto.randomUUID());
  const [pagando, setPagando] = useState(false);
  const [aprobada, setAprobada] = useState(null);
  const [error, setError] = useState(null);
  const [terminado, setTerminado] = useState(false);
  // Si la pasarela no dio resultado, el cobro pudo completarse igual: el reintento va con la misma tarjeta
  // para que la pasarela devuelva ese cobro y no abra otro.
  const [tarjetaFija, setTarjetaFija] = useState(false);

  // Cancelación de la reserva ya CONFIRMADA (SOF-75, HU-30): solo se ofrece tras el pago aprobado.
  const [motivo, setMotivo] = useState('');
  const [cancelando, setCancelando] = useState(false);
  const [cancelacion, setCancelacion] = useState(null);
  const [errorCancelacion, setErrorCancelacion] = useState(null);

  function cambiarTarjeta(evento) {
    setTarjeta(evento.target.value);
    setClaveIntento(crypto.randomUUID());
  }

  async function pagar() {
    setError(null);
    setPagando(true);
    try {
      setAprobada(await pagarReserva(reservaId, tarjeta, claveIntento));
      setTerminado(true);
    } catch (falla) {
      const conMensaje = falla.estado >= 400 && falla.estado !== 500;
      setError(conMensaje ? falla.message : MENSAJE_ERROR);
      // 402 (rechazado) y 409 (ya no se puede pagar) no se arreglan reintentando.
      setTerminado(falla.estado === 402 || falla.estado === 409);
      if (falla.estado === 502 || falla.estado === 504) setTarjetaFija(true);
    } finally {
      setPagando(false);
    }
  }

  async function cancelar() {
    setErrorCancelacion(null);
    setCancelando(true);
    try {
      setCancelacion(await cancelarReserva(reservaId, motivo));
    } catch (falla) {
      // Solo los 4xx traen un mensaje pensado para el usuario; un 5xx o sin conexión, el aviso genérico.
      setErrorCancelacion(falla.estado >= 400 && falla.estado < 500 ? falla.message : MENSAJE_ERROR_CANCELACION);
    } finally {
      setCancelando(false);
    }
  }

  return (
    <section className={estilos.pago} aria-label="Pago de la reserva">
      {!terminado && (
        <div className={estilos.formulario}>
          <label className={estilos.campo}>
            Tarjeta de prueba
            <select value={tarjeta} onChange={cambiarTarjeta} disabled={pagando || tarjetaFija}>
              {TARJETAS.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>{opcion.etiqueta}</option>
              ))}
            </select>
          </label>
          <button type="button" className={estilos.pagar} onClick={pagar} disabled={pagando}>
            {pagando ? 'Procesando pago…' : `Pagar ${FORMATO_PESOS.format(total)}`}
          </button>
        </div>
      )}
      {aprobada && (
        <output className={estilos.exito}>
          Pago aprobado: tu reserva está confirmada. Transacción {aprobada.transaccionId}.
        </output>
      )}
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      {aprobada && !cancelacion && (
        <div className={estilos.cancelacion}>
          <label className={estilos.campo}>
            Motivo de la cancelación
            <textarea value={motivo} onChange={(evento) => setMotivo(evento.target.value)} disabled={cancelando}
              maxLength={500} />
          </label>
          <button type="button" className={estilos.cancelar} onClick={cancelar}
                  disabled={cancelando || !motivo.trim()}>
            {cancelando ? 'Cancelando…' : 'Cancelar reserva'}
          </button>
        </div>
      )}
      {cancelacion && (
        <output className={estilos.exito}>
          Reserva cancelada: se devuelve {FORMATO_PESOS.format(cancelacion.valorDevolucion)}.
        </output>
      )}
      {errorCancelacion && <p role="alert" className={estilos.error}>{errorCancelacion}</p>}
    </section>
  );
}

PagoReserva.propTypes = {
  reservaId: PropTypes.number.isRequired,
  total: PropTypes.number.isRequired,
};
