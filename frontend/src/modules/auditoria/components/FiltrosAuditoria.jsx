import { useState } from 'react';
import PropTypes from 'prop-types';
import Icono from '../../../shared/components/Icono.jsx';
import { ACCIONES_AUDITORIA } from '../models/eventoAuditoria.js';
import estilos from './FiltrosAuditoria.module.css';

const VACIOS = { usuario: '', evento: '', entidad: '', entidadId: '', desde: '', hasta: '' };
const ERROR_RANGO = 'La fecha inicial no puede ser posterior a la final.';

function soloConValor(valores) {
  return Object.fromEntries(Object.entries(valores).filter(([, valor]) => valor.trim() !== ''));
}

/** Formulario de filtros de la consulta de auditoría. Emite solo los filtros con valor y no deja enviar desde > hasta. */
export default function FiltrosAuditoria({ valores = {}, alAplicar }) {
  const [filtros, setFiltros] = useState({ ...VACIOS, ...valores });
  const [errorRango, setErrorRango] = useState(false);

  function cambiar(nombre) {
    return (evento) => setFiltros((actuales) => ({ ...actuales, [nombre]: evento.target.value }));
  }

  function aplicar(evento) {
    evento.preventDefault();
    // Las fechas AAAA-MM-DD se ordenan igual como texto que como fechas.
    const incoherente = filtros.desde !== '' && filtros.hasta !== '' && filtros.desde > filtros.hasta;
    setErrorRango(incoherente);
    if (!incoherente) {
      alAplicar(soloConValor(filtros));
    }
  }

  return (
    <form className={estilos.formulario} onSubmit={aplicar} noValidate aria-label="Filtros de auditoría">
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="filtro-usuario">Usuario</label>
        <input id="filtro-usuario" className={estilos.control} value={filtros.usuario} onChange={cambiar('usuario')} />
      </div>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="filtro-evento">Evento</label>
        <select id="filtro-evento" className={estilos.control} value={filtros.evento} onChange={cambiar('evento')}>
          <option value="">Todos</option>
          {ACCIONES_AUDITORIA.map((accion) => <option key={accion} value={accion}>{accion}</option>)}
        </select>
      </div>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="filtro-entidad">Entidad</label>
        <input id="filtro-entidad" className={estilos.control} value={filtros.entidad} onChange={cambiar('entidad')} />
      </div>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="filtro-entidad-id">Id de la entidad</label>
        <input id="filtro-entidad-id" className={estilos.control} value={filtros.entidadId}
          onChange={cambiar('entidadId')} />
      </div>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="filtro-desde">Desde</label>
        <input id="filtro-desde" type="date" className={estilos.control} value={filtros.desde}
          aria-invalid={errorRango ? true : undefined} aria-describedby={errorRango ? 'filtro-rango-error' : undefined}
          onChange={cambiar('desde')} />
      </div>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="filtro-hasta">Hasta</label>
        <input id="filtro-hasta" type="date" className={estilos.control} value={filtros.hasta}
          aria-invalid={errorRango ? true : undefined} aria-describedby={errorRango ? 'filtro-rango-error' : undefined}
          onChange={cambiar('hasta')} />
      </div>
      {errorRango && <p id="filtro-rango-error" role="alert" className={estilos.error}>{ERROR_RANGO}</p>}
      <button type="submit" className={estilos.primario}>
        <Icono nombre="buscar" />
        Aplicar filtros
      </button>
    </form>
  );
}

FiltrosAuditoria.propTypes = {
  valores: PropTypes.shape({
    usuario: PropTypes.string,
    evento: PropTypes.string,
    entidad: PropTypes.string,
    entidadId: PropTypes.string,
    desde: PropTypes.string,
    hasta: PropTypes.string,
  }),
  alAplicar: PropTypes.func.isRequired,
};
