import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PropTypes from 'prop-types';
import { formatearFecha, formatearPesos } from '../../reservas/models/reserva.js';
import FiltroRangoFechas from '../components/FiltroRangoFechas.jsx';
import { nombreDeEstadoReserva, nombreDeRol, sumar } from '../models/dashboard.js';
import { consultarDashboard } from '../services/dashboardService.js';
import estilos from './DashboardPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar los indicadores. Intenta de nuevo.';

function rangoDe(parametros) {
  return Object.fromEntries(['desde', 'hasta'].filter((nombre) => parametros.get(nombre))
    .map((nombre) => [nombre, parametros.get(nombre)]));
}

function descripcionDe({ desde, hasta }) {
  if (desde && hasta) return `Del ${formatearFecha(desde)} al ${formatearFecha(hasta)}.`;
  if (desde) return `Desde el ${formatearFecha(desde)}.`;
  if (hasta) return `Hasta el ${formatearFecha(hasta)}.`;
  return 'Todo el histórico.';
}

function TablaConteos({ id, titulo, conteos, nombrar }) {
  return (
    <section className={estilos.bloque} aria-labelledby={id}>
      <h2 id={id} className={estilos.subtitulo}>{titulo}</h2>
      <table className={estilos.tabla}>
        <tbody>
          {Object.entries(conteos).map(([clave, valor]) => (
            <tr key={clave}>
              <th scope="row">{nombrar(clave)}</th>
              <td>{valor}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Total</th>
            <td>{sumar(conteos)}</td>
          </tr>
        </tfoot>
      </table>
    </section>
  );
}

TablaConteos.propTypes = {
  id: PropTypes.string.isRequired,
  titulo: PropTypes.string.isRequired,
  conteos: PropTypes.objectOf(PropTypes.number).isRequired,
  nombrar: PropTypes.func.isRequired,
};

/** Dashboard de indicadores del negocio para el administrador (RF-41, RF-42). */
export default function DashboardPage() {
  // El rango vive en la dirección: recargar la página recalcula con el mismo filtro.
  const [parametros, setParametros] = useSearchParams();
  const rango = rangoDe(parametros);
  const clave = parametros.toString();
  const [dashboard, setDashboard] = useState(null);
  const [error, setError] = useState(null);

  // Cada carga lleva un número: solo la última aplica su respuesta, y ninguna tras desmontar la página.
  const ultimaCarga = useRef(0);

  const cargar = useCallback(async () => {
    const numero = ++ultimaCarga.current;
    setError(null);
    setDashboard(null);
    try {
      const datos = await consultarDashboard(rangoDe(new URLSearchParams(clave)));
      if (numero === ultimaCarga.current) setDashboard(datos);
    } catch (fallo) {
      // Solo un 400 trae un mensaje pensado para el administrador; el resto va con el genérico.
      if (numero === ultimaCarga.current) setError(fallo.estado === 400 ? fallo.message : MENSAJE_ERROR);
    }
  }, [clave]);

  useEffect(() => {
    cargar();
    return () => {
      ultimaCarga.current += 1;
    };
  }, [cargar]);

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Dashboard de indicadores</h1>
      <p className={estilos.nota}>
        Cada indicador cuenta lo creado en el rango elegido, en hora de Bogotá. {descripcionDe(rango)}
      </p>
      <FiltroRangoFechas key={clave} valores={rango} alAplicar={(nuevo) => setParametros(new URLSearchParams(nuevo))} />
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      {!error && dashboard === null && <output className={estilos.nota}>Cargando indicadores…</output>}
      {!error && dashboard !== null && (
        <>
          <dl className={estilos.cifras}>
            <div className={estilos.cifra}>
              <dt>Usuarios</dt>
              <dd>{sumar(dashboard.usuariosPorRol)}</dd>
            </div>
            <div className={estilos.cifra}>
              <dt>Publicaciones activas</dt>
              <dd>{dashboard.publicacionesActivas}</dd>
            </div>
            <div className={estilos.cifra}>
              <dt>Reservas</dt>
              <dd>{sumar(dashboard.reservasPorEstado)}</dd>
            </div>
            <div className={estilos.cifra}>
              <dt>Ingresos estimados</dt>
              <dd>{formatearPesos(dashboard.ingresosEstimados)}</dd>
            </div>
          </dl>
          <p className={estilos.nota}>Los ingresos estimados suman el total de las reservas confirmadas, completadas y en revisión manual.</p>
          <div className={estilos.bloques}>
            <TablaConteos id="usuarios-titulo" titulo="Usuarios por rol" conteos={dashboard.usuariosPorRol}
              nombrar={nombreDeRol} />
            <TablaConteos id="reservas-titulo" titulo="Reservas por estado" conteos={dashboard.reservasPorEstado}
              nombrar={nombreDeEstadoReserva} />
            <section className={estilos.bloque} aria-labelledby="ciudades-titulo">
              <h2 id="ciudades-titulo" className={estilos.subtitulo}>Ciudades con mayor demanda</h2>
              {dashboard.ciudadesMayorDemanda.length === 0 ? (
                <p className={estilos.nota}>Todavía no hay reservas en este rango.</p>
              ) : (
                <ol className={estilos.ranking}>
                  {dashboard.ciudadesMayorDemanda.map(({ ciudad, reservas }) => (
                    <li key={ciudad}>
                      <span>{ciudad}</span>
                      <span>{reservas === 1 ? '1 reserva' : `${reservas} reservas`}</span>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </>
      )}
    </main>
  );
}
