import { useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import estilos from './RechazarSolicitudDialogo.module.css';

const LIMITE_MOTIVO = 500;

/**
 * Diálogo modal para rechazar una solicitud de reserva (SOF-79): pide un motivo obligatorio de hasta 500
 * caracteres con contador. `alConfirmar` recibe el motivo y devuelve una promesa; si se rechaza con un error,
 * su mensaje se muestra aquí para poder corregir el motivo. Escape o "Cancelar" lo cierran.
 */
export default function RechazarSolicitudDialogo({ titulo, alConfirmar, alCancelar }) {
  const [motivo, setMotivo] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState(null);
  const campo = useRef(null);

  useEffect(() => {
    campo.current?.focus();
  }, []);

  async function confirmar() {
    setError(null);
    setEnviando(true);
    try {
      await alConfirmar(motivo.trim());
    } catch (falla) {
      setError(falla.message);
      setEnviando(false);
    }
  }

  function alPulsarTecla(evento) {
    if (evento.key === 'Escape' && !enviando) alCancelar();
  }

  return (
    <div className={estilos.fondo}>
      <div role="dialog" aria-modal="true" aria-labelledby="rechazo-titulo" className={estilos.dialogo}
        onKeyDown={alPulsarTecla}>
        <h2 id="rechazo-titulo" className={estilos.titulo}>Rechazar solicitud</h2>
        <p className={estilos.nota}>{`Cuéntale al huésped por qué rechazas la solicitud de «${titulo}».`}</p>
        <label className={estilos.campo}>
          Motivo del rechazo
          <textarea ref={campo} value={motivo} maxLength={LIMITE_MOTIVO} disabled={enviando}
            onChange={(evento) => setMotivo(evento.target.value)} />
        </label>
        <span className={estilos.contador}>{`${motivo.length}/${LIMITE_MOTIVO}`}</span>
        {error && <p role="alert" className={estilos.error}>{error}</p>}
        <div className={estilos.acciones}>
          <button type="button" className={estilos.confirmar} onClick={confirmar}
            disabled={enviando || !motivo.trim()}>
            {enviando ? 'Rechazando…' : 'Confirmar rechazo'}
          </button>
          <button type="button" className={estilos.cancelar} onClick={alCancelar} disabled={enviando}>
            Cancelar
          </button>
        </div>
      </div>
    </div>
  );
}

RechazarSolicitudDialogo.propTypes = {
  titulo: PropTypes.string.isRequired,
  alConfirmar: PropTypes.func.isRequired,
  alCancelar: PropTypes.func.isRequired,
};
