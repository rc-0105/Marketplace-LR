import { useState } from 'react';
import PropTypes from 'prop-types';
import estilos from './FiltroRangoFechas.module.css';

const ERROR_RANGO = 'La fecha inicial no puede ser posterior a la final.';

/** Rango de fechas del dashboard (RF-42). Emite solo las fechas con valor y no deja enviar desde > hasta. */
export default function FiltroRangoFechas({ valores = {}, alAplicar }) {
  const [desde, setDesde] = useState(valores.desde ?? '');
  const [hasta, setHasta] = useState(valores.hasta ?? '');
  const [errorRango, setErrorRango] = useState(false);

  function aplicar(evento) {
    evento.preventDefault();
    // Las fechas AAAA-MM-DD se ordenan igual como texto que como fechas.
    const incoherente = desde !== '' && hasta !== '' && desde > hasta;
    setErrorRango(incoherente);
    if (!incoherente) {
      alAplicar({ ...(desde && { desde }), ...(hasta && { hasta }) });
    }
  }

  function limpiar() {
    setDesde('');
    setHasta('');
    setErrorRango(false);
    alAplicar({});
  }

  const invalido = errorRango ? true : undefined;
  const descripcion = errorRango ? 'rango-error' : undefined;

  return (
    <form className={estilos.formulario} onSubmit={aplicar} noValidate aria-label="Rango de fechas">
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="rango-desde">Desde</label>
        <input id="rango-desde" type="date" className={estilos.control} value={desde}
          aria-invalid={invalido} aria-describedby={descripcion} onChange={(evento) => setDesde(evento.target.value)} />
      </div>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="rango-hasta">Hasta</label>
        <input id="rango-hasta" type="date" className={estilos.control} value={hasta}
          aria-invalid={invalido} aria-describedby={descripcion} onChange={(evento) => setHasta(evento.target.value)} />
      </div>
      {errorRango && <p id="rango-error" role="alert" className={estilos.error}>{ERROR_RANGO}</p>}
      <div className={estilos.acciones}>
        <button type="submit" className={estilos.primario}>Aplicar</button>
        <button type="button" className={estilos.secundario} onClick={limpiar}>Ver todo</button>
      </div>
    </form>
  );
}

FiltroRangoFechas.propTypes = {
  valores: PropTypes.shape({
    desde: PropTypes.string,
    hasta: PropTypes.string,
  }),
  alAplicar: PropTypes.func.isRequired,
};
