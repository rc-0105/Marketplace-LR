import { useState } from 'react';
import PropTypes from 'prop-types';
import { pagarReserva } from '../services/reservaService.js';
import estilos from './PagoReserva.module.css';

const MENSAJE_ERROR = 'No pudimos procesar el pago. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

// Tarjetas de prueba de Stripe: ninguna hace un cobro real (SOF-34). Los últimos dígitos son los de la
// tarjeta de prueba equivalente de Stripe, para que el huésped reconozca cuál está usando.
const TARJETAS = [
  { valor: 'pm_card_visa', marca: 'Visa', digitos: '4242', resultado: 'Pago aprobado' },
  { valor: 'pm_card_mastercard', marca: 'Mastercard', digitos: '4444', resultado: 'Pago aprobado' },
  { valor: 'pm_card_chargeDeclined', marca: 'Visa', digitos: '0002', resultado: 'La tarjeta es rechazada' },
  { valor: 'pm_card_chargeDeclinedInsufficientFunds', marca: 'Visa', digitos: '9995', resultado: 'Sin fondos suficientes' },
];

function LogoMarca({ marca }) {
  if (marca === 'Mastercard') {
    return (
      <span className={estilos.logoMastercard} aria-hidden="true">
        <span className={estilos.circuloRojo} />
        <span className={estilos.circuloAmarillo} />
      </span>
    );
  }
  return <span className={estilos.logoVisa} aria-hidden="true">VISA</span>;
}

LogoMarca.propTypes = { marca: PropTypes.string.isRequired };

function IconoCandado() {
  return (
    <svg className={estilos.candado} viewBox="0 0 24 24" aria-hidden="true">
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  );
}

/**
 * Pago de una reserva PENDIENTE con la pasarela en modo prueba (SOF-34). Aprobado o rechazado, el
 * resultado es definitivo y el botón desaparece; si la pasarela no respondió o falló (5xx), la reserva
 * sigue apartada y se puede reintentar. La cancelación de la reserva confirmada se hace desde su detalle.
 */
export default function PagoReserva({ reservaId, total, onAprobado }) {
  const [tarjeta, setTarjeta] = useState(TARJETAS[0].valor);
  const [claveIntento, setClaveIntento] = useState(() => crypto.randomUUID());
  const [pagando, setPagando] = useState(false);
  const [aprobada, setAprobada] = useState(null);
  const [error, setError] = useState(null);
  const [terminado, setTerminado] = useState(false);
  // Si la pasarela no dio resultado, el cobro pudo completarse igual: el reintento va con la misma tarjeta
  // para que la pasarela devuelva ese cobro y no abra otro.
  const [tarjetaFija, setTarjetaFija] = useState(false);

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
      if (onAprobado) onAprobado();
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

  const elegida = TARJETAS.find((opcion) => opcion.valor === tarjeta);

  return (
    <section className={estilos.pago} aria-label="Pago de la reserva">
      {!terminado && (
        <div className={estilos.formulario}>
          {/* Vista previa decorativa de la tarjeta elegida; los datos accesibles están en las opciones. */}
          <div className={`${estilos.tarjeta} ${elegida.marca === 'Mastercard' ? estilos.tarjetaMastercard : ''}`}
            aria-hidden="true">
            <div className={estilos.tarjetaFila}>
              <span className={estilos.chip} />
              <span className={estilos.tarjetaEtiqueta}>Tarjeta de prueba</span>
            </div>
            <span className={estilos.tarjetaNumero}>{`•••• •••• •••• ${elegida.digitos}`}</span>
            <div className={estilos.tarjetaFila}>
              <span className={estilos.tarjetaDato}>
                <small>Vence</small>
                12/34
              </span>
              <LogoMarca marca={elegida.marca} />
            </div>
          </div>

          <fieldset className={estilos.metodos} disabled={pagando || tarjetaFija}>
            <legend className={estilos.leyenda}>Elige el método de pago</legend>
            {TARJETAS.map((opcion) => (
              <label key={opcion.valor}
                className={`${estilos.metodo} ${tarjeta === opcion.valor ? estilos.elegido : ''}`}>
                <input type="radio" name="metodo-pago" value={opcion.valor} checked={tarjeta === opcion.valor}
                  onChange={cambiarTarjeta} className={estilos.radio} />
                <span className={estilos.marca}><LogoMarca marca={opcion.marca} /></span>
                <span className={estilos.datos}>
                  <span className={estilos.numero}>{`${opcion.marca} terminada en ${opcion.digitos}`}</span>
                  <span className={estilos.resultado}>{`Tarjeta de prueba · ${opcion.resultado}`}</span>
                </span>
              </label>
            ))}
          </fieldset>
          <button type="button" className={estilos.pagar} onClick={pagar} disabled={pagando}>
            <IconoCandado />
            {pagando ? 'Procesando pago…' : `Pagar ${FORMATO_PESOS.format(total)}`}
          </button>
          <p className={estilos.confianza}>Conexión cifrada · Ningún pago de prueba hace un cobro real</p>
        </div>
      )}
      {aprobada && (
        <output className={estilos.exito}>Pago aceptado: tu reserva está confirmada.</output>
      )}
      {error && <p role="alert" className={estilos.error}>{error}</p>}
    </section>
  );
}

PagoReserva.propTypes = {
  reservaId: PropTypes.number.isRequired,
  total: PropTypes.number.isRequired,
  onAprobado: PropTypes.func,
};
