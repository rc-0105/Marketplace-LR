import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import FiltroRangoFechas from '../components/FiltroRangoFechas.jsx';
import { consultarReporte, exportarReporte, guardarArchivo } from '../services/reportesService.js';
import estilos from './ReportesPage.module.css';

const TIPOS = [
  { valor: 'reservas', nombre: 'Reservas' },
  { valor: 'usuarios', nombre: 'Usuarios' },
  { valor: 'publicaciones', nombre: 'Publicaciones' },
  { valor: 'ingresos', nombre: 'Ingresos estimados' },
  { valor: 'demanda', nombre: 'Demanda por ciudad y tipo de alojamiento' },
];
const TIPO_INICIAL = 'reservas';
const MENSAJE_ERROR = 'No pudimos cargar el reporte. Intenta de nuevo.';
const MENSAJE_ERROR_EXPORTAR = 'No pudimos exportar el reporte. Intenta de nuevo.';

function rangoDe(parametros) {
  return Object.fromEntries(['desde', 'hasta'].filter((nombre) => parametros.get(nombre))
    .map((nombre) => [nombre, parametros.get(nombre)]));
}

function tipoDe(parametros) {
  const tipo = parametros.get('tipo');
  return TIPOS.some(({ valor }) => valor === tipo) ? tipo : TIPO_INICIAL;
}

function textoDe(celda) {
  return celda === null || celda === undefined ? '' : String(celda);
}

/**
 * Reportes del administrador (RF-43, HU-28): muestra el reporte elegido y lo exporta a Excel o PDF.
 * La tabla y los archivos salen de la misma consulta de la API, así que traen las mismas filas.
 */
export default function ReportesPage() {
  // Tipo y rango viven en la dirección, como en el dashboard: recargar repite la consulta.
  const [parametros, setParametros] = useSearchParams();
  const tipo = tipoDe(parametros);
  const rango = rangoDe(parametros);
  const clave = parametros.toString();
  const [reporte, setReporte] = useState(null);
  const [error, setError] = useState(null);
  const [exportando, setExportando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const [errorExportar, setErrorExportar] = useState(null);

  // Solo la última carga aplica su respuesta, y ninguna tras desmontar la página.
  const ultimaCarga = useRef(0);

  const cargar = useCallback(async () => {
    const numero = ++ultimaCarga.current;
    const actuales = new URLSearchParams(clave);
    setError(null);
    setReporte(null);
    setAviso(null);
    setErrorExportar(null);
    try {
      const datos = await consultarReporte(tipoDe(actuales), rangoDe(actuales));
      if (numero === ultimaCarga.current) setReporte(datos);
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

  function cambiarParametros(nuevoTipo, nuevoRango) {
    setParametros(new URLSearchParams({ tipo: nuevoTipo, ...nuevoRango }));
  }

  async function exportar(formato) {
    setExportando(true);
    setAviso(null);
    setErrorExportar(null);
    try {
      const archivo = await exportarReporte(tipo, formato, rango);
      guardarArchivo(archivo);
      setAviso(`Descargaste ${archivo.nombre}.`);
    } catch (fallo) {
      setErrorExportar(fallo.estado === 400 ? fallo.message : MENSAJE_ERROR_EXPORTAR);
    } finally {
      setExportando(false);
    }
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Reportes</h1>
      <p className={estilos.nota}>
        Elige un reporte y un rango de fechas, en hora de Bogotá. Cada exportación queda registrada con tu
        usuario, la fecha y los filtros.
      </p>
      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor="reporte-tipo">Reporte</label>
        <select id="reporte-tipo" className={estilos.control} value={tipo}
          onChange={(evento) => cambiarParametros(evento.target.value, rango)}>
          {TIPOS.map(({ valor, nombre }) => <option key={valor} value={valor}>{nombre}</option>)}
        </select>
      </div>
      <FiltroRangoFechas key={clave} valores={rango} alAplicar={(nuevo) => cambiarParametros(tipo, nuevo)} />
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      {!error && reporte === null && <output className={estilos.nota}>Cargando reporte…</output>}
      {!error && reporte !== null && (
        <section className={estilos.resultado}>
          <div className={estilos.acciones}>
            <button type="button" className={estilos.primario} disabled={exportando}
              onClick={() => exportar('xlsx')}>Exportar a Excel</button>
            <button type="button" className={estilos.secundario} disabled={exportando}
              onClick={() => exportar('pdf')}>Exportar a PDF</button>
          </div>
          {aviso && <output className={estilos.aviso}>{aviso}</output>}
          {errorExportar && <p role="alert" className={estilos.error}>{errorExportar}</p>}
          <p className={estilos.nota}>
            {reporte.filas.length === 0
              ? 'No hay registros en este rango. El archivo se exporta solo con los encabezados.'
              : `${reporte.filas.length === 1 ? '1 fila' : `${reporte.filas.length} filas`}. `
                + 'Los archivos exportados traen exactamente estas filas.'}
          </p>
          <div className={estilos.desplazable}>
            <table className={estilos.tabla}>
              <caption className={estilos.leyenda}>{reporte.titulo}</caption>
              <thead>
                <tr>
                  {reporte.encabezados.map((encabezado) => <th key={encabezado} scope="col">{encabezado}</th>)}
                </tr>
              </thead>
              <tbody>
                {reporte.filas.map((fila, numero) => (
                  // Las filas no traen un identificador común a todos los reportes; su posición es estable.
                  <tr key={numero}>
                    {fila.map((celda, columna) => (
                      <td key={columna}>{textoDe(celda)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </main>
  );
}
