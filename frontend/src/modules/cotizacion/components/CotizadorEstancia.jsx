import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import { crearReserva } from '../../reservas/services/reservaService.js';
import { cotizarEstancia } from '../services/cotizacionService.js';
import estilos from './CotizadorEstancia.module.css';

const MENSAJE_ERROR = 'No pudimos calcular la cotización. Intenta de nuevo.';
const MENSAJE_ERROR_RESERVA = 'No pudimos confirmar la reserva. Intenta de nuevo.';
const MENSAJE_RESERVA_CREADA = 'Reserva creada, queda pendiente de pago.';
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
  const [reservando, setReservando] = useState(false);
  const [mensajeReserva, setMensajeReserva] = useState(null);
  const [errorReserva, setErrorReserva] = useState(null);

  function cambiarFecha(evento) {
    const { name, value } = evento.target;
    setFechas((actuales) => ({ ...actuales, [name]: value }));
  }

  async function calcular(servicios) {
    setErrorCotizacion(null);
    setMensajeReserva(null);
    setErrorReserva(null);
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

  // La reserva confirma la misma estancia ya cotizada: la API vuelve a cotizar para fijar el
  // precio y revalidar que todo siga vigente (AC4, AC5), y el cruce de fechas lo decide ella (AC2, AC3).
  async function confirmarReserva() {
    setErrorReserva(null);
    setMensajeReserva(null);
    setReservando(true);
    try {
      await crearReserva({ publicacionId, ...fechas, serviciosAdicionalesIds: elegidos });
      setMensajeReserva(MENSAJE_RESERVA_CREADA);
    } catch (error) {
      setErrorReserva(error.estado >= 400 && error.estado < 500 ? error.message : MENSAJE_ERROR_RESERVA);
    } finally {
      setReservando(false);
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
        <>
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
          <button type="button" className={estilos.reservar} onClick={confirmarReserva} disabled={reservando}>
            {reservando ? 'Confirmando…' : 'Confirmar reserva'}
          </button>
          {mensajeReserva && <output className={estilos.exito}>{mensajeReserva}</output>}
          {errorReserva && <p role="alert" className={estilos.error}>{errorReserva}</p>}
        </>
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
