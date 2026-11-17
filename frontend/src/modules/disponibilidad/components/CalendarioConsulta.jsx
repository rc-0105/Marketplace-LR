import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';
import { consultarCalendario } from '../services/disponibilidadService.js';
import CalendarioDisponibilidad from './CalendarioDisponibilidad.jsx';
import estilos from './CalendarioConsulta.module.css';

const MENSAJE_ERROR = 'No pudimos cargar el calendario. Intenta de nuevo.';
const MENSAJE_NO_DISPONIBLE = 'Esta publicación no está disponible.';

/**
 * Pide el calendario de la publicación y lo muestra con su estado de carga, error y reintento.
 * Ramifica por el `estado` HTTP del error, nunca por su texto: el 404 no distingue si la
 * publicación no existe o no es visible.
 * Si `recarga` cambia, vuelve a pedirlo sin quitar la grilla ni el mes que se está viendo.
 * @param {{publicacionId: number | string, recarga?: number}} props
 */
export default function CalendarioConsulta({ publicacionId, recarga = 0 }) {
  const [resultado, setResultado] = useState({ calendario: null, error: null });
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    // Tras un error (reintento) vuelve a mostrar la carga; con un calendario a la vista, lo conserva hasta que llegue el nuevo.
    setResultado((actual) => (actual.error ? { calendario: null, error: null } : actual));
    consultarCalendario(publicacionId)
      .then((calendario) => vigente && setResultado({ calendario, error: null }))
      .catch((error) => vigente && setResultado({ calendario: null, error }));
    // Si el componente se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, [publicacionId, intento, recarga]);

  const { calendario, error } = resultado;
  if (error) {
    const noDisponible = error.estado === 404;
    return (
      <div className={estilos.error}>
        <p role="alert" className={estilos.mensaje}>{noDisponible ? MENSAJE_NO_DISPONIBLE : MENSAJE_ERROR}</p>
        {!noDisponible && (
          <button type="button" className={estilos.reintentar} onClick={() => setIntento((n) => n + 1)}>
            Reintentar
          </button>
        )}
      </div>
    );
  }
  if (!calendario) {
    return <output className={estilos.carga}>Cargando calendario…</output>;
  }
  return <CalendarioDisponibilidad calendario={calendario} />;
}

CalendarioConsulta.propTypes = {
  publicacionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  recarga: PropTypes.number,
};
