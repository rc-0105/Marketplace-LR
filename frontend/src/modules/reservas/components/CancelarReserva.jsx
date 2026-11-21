import { useState } from 'react';
import PropTypes from 'prop-types';
import { formatearPesos } from '../models/reserva.js';
import { cancelarReserva } from '../services/reservaService.js';
import estilos from './CancelarReserva.module.css';

const MENSAJE_ERROR = 'No pudimos cancelar la reserva. Intenta de nuevo.';

/**
 * Cancelación de una reserva CONFIRMADA (SOF-75, HU-30). Primero se ofrece el botón; al pulsarlo se pide el
 * motivo y se confirma. Al cancelar se muestra la devolución y se avisa a la página para que refresque la reserva.
 */
export default function CancelarReserva({ reservaId, onCancelada }) {
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [cancelando, setCancelando] = useState(false);
  const [cancelacion, setCancelacion] = useState(null);
  const [error, setError] = useState(null);

  async function confirmar() {
    setError(null);
    setCancelando(true);
    try {
      const resultado = await cancelarReserva(reservaId, motivo);
      setCancelacion(resultado);
      if (onCancelada) onCancelada(resultado);
    } catch (falla) {
      // Solo los 4xx traen un mensaje pensado para el usuario; un 5xx o sin conexión, el aviso genérico.
      setError(falla.estado >= 400 && falla.estado < 500 ? falla.message : MENSAJE_ERROR);
    } finally {
      setCancelando(false);
    }
  }

  function cerrar() {
    setAbierto(false);
    setMotivo('');
    setError(null);
  }

  if (cancelacion) {
    return (
      <output className={estilos.exito}>
        Reserva cancelada: se devuelve {formatearPesos(cancelacion.valorDevolucion)}.
      </output>
    );
  }

  if (!abierto) {
    return (
      <button type="button" className={estilos.abrir} onClick={() => setAbierto(true)}>
        Cancelar reserva
      </button>
    );
  }

  return (
    <section className={estilos.panel} aria-labelledby="cancelar-titulo">
      <h2 id="cancelar-titulo" className={estilos.titulo}>Cancelar reserva</h2>
      <p className={estilos.nota}>Cuéntanos por qué cancelas. La devolución se calcula al confirmar.</p>
      <label className={estilos.campo}>
        Motivo de la cancelación
        <textarea value={motivo} onChange={(evento) => setMotivo(evento.target.value)} disabled={cancelando}
          maxLength={500} />
      </label>
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      <div className={estilos.acciones}>
        <button type="button" className={estilos.confirmar} onClick={confirmar}
          disabled={cancelando || !motivo.trim()}>
          {cancelando ? 'Cancelando…' : 'Confirmar cancelación'}
        </button>
        <button type="button" className={estilos.volver} onClick={cerrar} disabled={cancelando}>
          No cancelar
        </button>
      </div>
    </section>
  );
}

CancelarReserva.propTypes = {
  reservaId: PropTypes.number.isRequired,
  onCancelada: PropTypes.func,
};
