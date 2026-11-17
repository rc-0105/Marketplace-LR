import PropTypes from 'prop-types';
import { useMemo, useState } from 'react';
import { ETIQUETAS_ESTADO, mesesDe } from '../models/calendario.js';
import { fechaLegible } from '../models/periodo.js';
import estilos from './CalendarioDisponibilidad.module.css';

const DIAS_SEMANA = [
  ['Lunes', 'L'],
  ['Martes', 'M'],
  ['Miércoles', 'X'],
  ['Jueves', 'J'],
  ['Viernes', 'V'],
  ['Sábado', 'S'],
  ['Domingo', 'D'],
];

const CLASE_ESTADO = {
  DISPONIBLE: estilos.disponible,
  RESERVADO: estilos.reservado,
  BLOQUEADO: estilos.bloqueado,
};

// El estado va en texto en el nombre accesible: el color y el marcador solo lo refuerzan.
function nombreDelDia({ fecha, estado, reservaId }) {
  const partes = [fechaLegible(fecha)];
  if (estado) {
    partes.push(ETIQUETAS_ESTADO[estado]);
  }
  if (reservaId !== undefined) {
    partes.push(`Reserva ${reservaId}`);
  }
  return partes.join(', ');
}

/**
 * Un mes a la vez de la ventana que entregó la API. Solo presenta: no calcula estados ni decide quién ve
 * `reservaId`; lo muestra únicamente si viene en el día (RF-25).
 * @param {{calendario: import('../models/calendario.js').Calendario}} props
 */
export default function CalendarioDisponibilidad({ calendario }) {
  const meses = useMemo(() => mesesDe(calendario.dias), [calendario.dias]);
  const [claveElegida, setClaveElegida] = useState(null);
  // Si el calendario se recarga, se conserva el mes mostrado mientras siga existiendo.
  const indice = Math.max(0, meses.findIndex((mes) => mes.clave === claveElegida));
  const mes = meses[indice];

  return (
    <section className={estilos.calendario} aria-label="Calendario de disponibilidad">
      {!calendario.tienePeriodos && (
        <p role="status" className={estilos.aviso}>
          La publicación aún no tiene periodos de disponibilidad.
        </p>
      )}
      {mes && (
        <>
          <div className={estilos.navegacion}>
            <button type="button" className={estilos.boton} disabled={indice === 0}
              onClick={() => setClaveElegida(meses[indice - 1].clave)}>
              Mes anterior
            </button>
            <button type="button" className={estilos.boton} disabled={indice === meses.length - 1}
              onClick={() => setClaveElegida(meses[indice + 1].clave)}>
              Mes siguiente
            </button>
          </div>
          <table className={estilos.tabla}>
            <caption className={estilos.mes}>{mes.titulo}</caption>
            <thead>
              <tr>
                {DIAS_SEMANA.map(([nombre, inicial]) => (
                  <th key={nombre} scope="col" abbr={nombre} className={estilos.encabezado}>{inicial}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {mes.semanas.map((semana) => (
                <tr key={semana.find(Boolean).fecha}>
                  {semana.map((dia, posicion) => (dia ? (
                    <td key={dia.fecha} aria-label={nombreDelDia(dia)}
                      className={`${estilos.dia} ${CLASE_ESTADO[dia.estado] ?? estilos.sinEstado}`}>
                      {Number(dia.fecha.slice(8))}
                    </td>
                  ) : (
                    <td key={`vacio-${posicion}`} className={estilos.vacio} />
                  )))}
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      <ul className={estilos.leyenda} aria-label="Leyenda">
        {Object.entries(ETIQUETAS_ESTADO).map(([estado, etiqueta]) => (
          <li key={estado} className={estilos.referencia}>
            <span className={`${estilos.muestra} ${CLASE_ESTADO[estado]}`} aria-hidden="true" />
            {etiqueta}
          </li>
        ))}
      </ul>
    </section>
  );
}

CalendarioDisponibilidad.propTypes = {
  calendario: PropTypes.shape({
    tienePeriodos: PropTypes.bool.isRequired,
    dias: PropTypes.arrayOf(PropTypes.shape({
      fecha: PropTypes.string.isRequired,
      estado: PropTypes.string,
      reservaId: PropTypes.number,
    })).isRequired,
  }).isRequired,
};
