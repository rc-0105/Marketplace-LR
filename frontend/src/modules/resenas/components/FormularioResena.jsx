import { useState } from 'react';
import PropTypes from 'prop-types';
import { CALIFICACION_MAXIMA, CALIFICACIONES, CONTENIDO_MAXIMO } from '../models/resena.js';
import { crearResena } from '../services/resenaService.js';
import estilos from './FormularioResena.module.css';

const MENSAJE_ERROR = 'No pudimos publicar tu reseña. Intenta de nuevo.';

/**
 * Formulario con el que el huésped califica de 1 a 5 y comenta su estancia ya terminada (SOF-80, HU-35). Una
 * reseña publicada es definitiva: el formulario desaparece. Si la API dice que ya existe una (409), tampoco
 * tiene sentido reintentar y se muestra su mensaje.
 */
export default function FormularioResena({ reservaId }) {
  const [calificacion, setCalificacion] = useState(null);
  const [contenido, setContenido] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [publicada, setPublicada] = useState(false);
  const [error, setError] = useState(null);
  const [terminado, setTerminado] = useState(false);

  async function publicar(evento) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await crearResena(reservaId, { calificacion, contenido: contenido.trim() });
      setPublicada(true);
      setTerminado(true);
    } catch (falla) {
      // Solo los 4xx traen un mensaje pensado para el usuario; un 5xx o sin conexión, el aviso genérico.
      setError(falla.estado >= 400 && falla.estado < 500 ? falla.message : MENSAJE_ERROR);
      setTerminado(falla.estado === 409);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section className={estilos.resena} aria-labelledby="resena-titulo">
      <h2 id="resena-titulo" className={estilos.titulo}>Cuéntanos cómo te fue</h2>
      {!terminado && (
        <form className={estilos.formulario} onSubmit={publicar} aria-label="Reseñar la estancia">
          <fieldset className={estilos.calificacion} disabled={enviando}>
            <legend>Calificación</legend>
            <div className={estilos.opciones}>
              {CALIFICACIONES.map((valor) => (
                <label key={valor} className={estilos.opcion}>
                  <input type="radio" name="calificacion" value={valor} checked={calificacion === valor}
                    onChange={() => setCalificacion(valor)} />
                  <span>{valor}</span>
                  <span className={estilos.lectorPantalla}>{` de ${CALIFICACION_MAXIMA}`}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <label className={estilos.campo}>
            Comentario
            <textarea value={contenido} onChange={(evento) => setContenido(evento.target.value)}
              maxLength={CONTENIDO_MAXIMO} rows={4} disabled={enviando} />
          </label>
          <button type="submit" className={estilos.publicar}
            disabled={enviando || calificacion === null || !contenido.trim()}>
            {enviando ? 'Publicando…' : 'Publicar reseña'}
          </button>
        </form>
      )}
      {publicada && <output className={estilos.exito}>Gracias por tu reseña: ya quedó publicada.</output>}
      {error && <p role="alert" className={estilos.error}>{error}</p>}
    </section>
  );
}

FormularioResena.propTypes = {
  reservaId: PropTypes.number.isRequired,
};
