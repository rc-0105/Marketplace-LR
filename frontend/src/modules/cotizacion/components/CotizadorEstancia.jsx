import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import { cotizarEstancia } from '../services/cotizacionService.js';
import estilos from './CotizadorEstancia.module.css';

const MENSAJE_ERROR = 'No pudimos calcular la cotización. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

/**
 * Cotizador de una estancia: con las fechas muestra el desglose del precio, y cada servicio
 * adicional que se agrega o se quita lo recalcula (RF-32, RF-33).
 */
export default function CotizadorEstancia({
  publicacionId, serviciosAdicionales = [], llegadaInicial = '', salidaInicial = '',
}) {
  const [fechas, setFechas] = useState({ llegada: llegadaInicial, salida: salidaInicial });
  const [elegidos, setElegidos] = useState([]);
  const [errores, setErrores] = useState({});
  const [cotizacion, setCotizacion] = useState(null);
  const [errorCotizacion, setErrorCotizacion] = useState(null);
  const [calculando, setCalculando] = useState(false);

  function cambiarFecha(evento) {
    const { name, value } = evento.target;
    setFechas((actuales) => ({ ...actuales, [name]: value }));
  }

  async function calcular(servicios) {
    setErrorCotizacion(null);
    setCalculando(true);
    try {
      setCotizacion(await cotizarEstancia({ publicacionId, ...fechas, serviciosAdicionales: servicios }));
    } catch (error) {
      setCotizacion(null);
      setErrorCotizacion(error.estado === 400 ? error.message : MENSAJE_ERROR);
    } finally {
      setCalculando(false);
    }
  }

  function cotizar(evento) {
    evento.preventDefault();
    const invalidos = {};
    if (!fechas.llegada) invalidos.llegada = 'Indica la fecha de llegada.';
    if (!fechas.salida) invalidos.salida = 'Indica la fecha de salida.';
    setErrores(invalidos);
    if (Object.keys(invalidos).length > 0) return;
    calcular(elegidos);
  }

  // Con una cotización a la vista, agregar o quitar un servicio recalcula el total en el momento.
  function alternarServicio(id) {
    const nuevos = elegidos.includes(id) ? elegidos.filter((actual) => actual !== id) : [...elegidos, id];
    setElegidos(nuevos);
    if (cotizacion) calcular(nuevos);
  }

  return (
    <section>
      <h2 className={estilos.seccion}>Cotiza tu estancia</h2>
      <form className={estilos.formulario} onSubmit={cotizar} noValidate>
        <Campo id="llegada" etiqueta="Llegada" type="date" value={fechas.llegada} onChange={cambiarFecha}
          error={errores.llegada} />
        <Campo id="salida" etiqueta="Salida" type="date" value={fechas.salida} onChange={cambiarFecha}
          error={errores.salida} />
        {serviciosAdicionales.length > 0 && (
          <fieldset className={estilos.adicionales}>
            <legend>Servicios adicionales</legend>
            {serviciosAdicionales.map((adicional) => (
              <label key={adicional.id} className={estilos.adicional}>
                <input type="checkbox" checked={elegidos.includes(adicional.id)}
                  onChange={() => alternarServicio(adicional.id)} />
                {adicional.nombre} ({FORMATO_PESOS.format(adicional.costo)})
              </label>
            ))}
          </fieldset>
        )}
        <button type="submit" className={estilos.cotizar} disabled={calculando}>
          {calculando ? 'Calculando…' : 'Cotizar'}
        </button>
      </form>

      {errorCotizacion && <p role="alert" className={estilos.error}>{errorCotizacion}</p>}

      {cotizacion && (
        <dl className={estilos.desglose} aria-label="Desglose de la cotización">
          <div><dt>Noches</dt><dd>{cotizacion.noches}</dd></div>
          <div><dt>Precio base</dt><dd>{FORMATO_PESOS.format(cotizacion.precioBase)}</dd></div>
          <div><dt>Tarifa de limpieza</dt><dd>{FORMATO_PESOS.format(cotizacion.tarifaLimpieza)}</dd></div>
          <div><dt>Tarifa de servicio</dt><dd>{FORMATO_PESOS.format(cotizacion.tarifaServicio)}</dd></div>
          {cotizacion.serviciosAdicionales.map((adicional) => (
            <div key={adicional.id}><dt>{adicional.nombre}</dt><dd>{FORMATO_PESOS.format(adicional.costo)}</dd></div>
          ))}
          <div><dt>Descuentos</dt><dd>{FORMATO_PESOS.format(cotizacion.descuento)}</dd></div>
          <div className={estilos.total}><dt>Total</dt><dd>{FORMATO_PESOS.format(cotizacion.total)}</dd></div>
        </dl>
      )}
    </section>
  );
}

CotizadorEstancia.propTypes = {
  publicacionId: PropTypes.number.isRequired,
  serviciosAdicionales: PropTypes.arrayOf(PropTypes.shape({
    id: PropTypes.number.isRequired,
    nombre: PropTypes.string.isRequired,
    costo: PropTypes.number.isRequired,
  })),
  llegadaInicial: PropTypes.string,
  salidaInicial: PropTypes.string,
};
