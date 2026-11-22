import { useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Link, useNavigate } from 'react-router-dom';
import Campo from '../../../shared/components/Campo.jsx';
import { formatearPlazo } from '../../reservas/models/reserva.js';
import { crearReserva } from '../../reservas/services/reservaService.js';
import { cotizarEstancia } from '../services/cotizacionService.js';
import estilos from './CotizadorEstancia.module.css';

const MENSAJE_ERROR = 'No pudimos calcular la cotización. Intenta de nuevo.';
const MENSAJE_ERROR_RESERVA = 'No pudimos confirmar la reserva. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

/**
 * Cotizador de una estancia: con las fechas muestra el desglose del precio, y cada servicio
 * adicional que se agrega o se quita lo recalcula (RF-32, RF-33).
 *
 * La cotización mostrada guarda las fechas y los servicios con que se calculó: "Confirmar reserva"
 * reserva exactamente eso. Si luego se edita una fecha o un servicio, la cotización se descarta
 * (y con ella el botón) hasta volver a cotizar, para no reservar algo distinto de lo que se ve.
 * Creada la reserva (PENDIENTE), el huésped pasa al portal de pagos para pagarla (SOF-34).
 */
export default function CotizadorEstancia({
  publicacionId, serviciosAdicionales = [], llegadaInicial = '', salidaInicial = '',
}) {
  const [fechas, setFechas] = useState({ llegada: llegadaInicial, salida: salidaInicial });
  const [elegidos, setElegidos] = useState([]);
  const [errores, setErrores] = useState({});
  // La cotización a la vista y los datos con que se calculó, que son los que confirma la reserva.
  const [cotizada, setCotizada] = useState(null);
  const [errorCotizacion, setErrorCotizacion] = useState(null);
  const [calculando, setCalculando] = useState(false);
  const [reservando, setReservando] = useState(false);
  const [errorReserva, setErrorReserva] = useState(null);
  // Reserva ya pagada del huésped con esta misma estancia: la API la devuelve en vez de crear otra.
  const [yaReservada, setYaReservada] = useState(null);
  // Reserva creada en una publicación con aprobación del anfitrión (SOF-79): no hay pago hasta que responda.
  const [solicitada, setSolicitada] = useState(null);
  const navegar = useNavigate();
  // Cada edición o cálculo invalida las respuestas en vuelo: una cotización tardía no debe reaparecer.
  const solicitud = useRef(0);

  function descartarCotizacion() {
    solicitud.current += 1;
    setCotizada(null);
    setCalculando(false);
    setErrorReserva(null);
    setYaReservada(null);
    setSolicitada(null);
  }

  function cambiarFecha(evento) {
    const { name, value } = evento.target;
    setFechas((actuales) => ({ ...actuales, [name]: value }));
    descartarCotizacion();
  }

  async function calcular(servicios) {
    descartarCotizacion();
    const actual = solicitud.current;
    const { llegada, salida } = fechas;
    setErrorCotizacion(null);
    setCalculando(true);
    try {
      const resultado = await cotizarEstancia({ publicacionId, llegada, salida, serviciosAdicionales: servicios });
      // Cada cotización es un intento de reserva con su propia clave de idempotencia (SOF-77): reintentar
      // la confirmación de esta misma cotización reusa la clave y no crea una segunda reserva.
      if (actual === solicitud.current) {
        setCotizada({ cotizacion: resultado, llegada, salida, servicios, clave: crypto.randomUUID() });
      }
    } catch (error) {
      if (actual === solicitud.current) {
        setErrorCotizacion(error.estado === 400 ? error.message : MENSAJE_ERROR);
      }
    } finally {
      if (actual === solicitud.current) setCalculando(false);
    }
  }

  // La reserva confirma la misma estancia ya cotizada: la API vuelve a cotizar para fijar el
  // precio y revalidar que todo siga vigente (AC4, AC5), y el cruce de fechas lo decide ella (AC2, AC3).
  async function confirmarReserva() {
    setErrorReserva(null);
    setReservando(true);
    try {
      const { llegada, salida, servicios, clave } = cotizada;
      const reserva = await crearReserva({ publicacionId, llegada, salida, serviciosAdicionalesIds: servicios }, clave);
      // Si ya la tenía confirmada no hay nada que pagar: se avisa en vez de abrir el portal de pagos.
      if (reserva.estado === 'CONFIRMADA') {
        setYaReservada(reserva);
        setReservando(false);
        return;
      }
      // Con aprobación del anfitrión la reserva es solo una solicitud: se paga después, si la aprueba.
      if (reserva.estado === 'PENDIENTE_APROBACION') {
        setSolicitada(reserva);
        setReservando(false);
        return;
      }
      navegar(`/huesped/reservas/${reserva.id}/pago`);
    } catch (error) {
      setErrorReserva(error.estado >= 400 && error.estado < 500 ? error.message : MENSAJE_ERROR_RESERVA);
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
    if (cotizada) calcular(nuevos);
    else descartarCotizacion();
  }

  return (
    <section className={estilos.tarjeta}>
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

      {cotizada && (
        <>
          <dl className={estilos.desglose} aria-label="Desglose de la cotización">
            <div><dt>Noches</dt><dd>{cotizada.cotizacion.noches}</dd></div>
            <div><dt>Precio base</dt><dd>{FORMATO_PESOS.format(cotizada.cotizacion.precioBase)}</dd></div>
            <div><dt>Tarifa de limpieza</dt><dd>{FORMATO_PESOS.format(cotizada.cotizacion.tarifaLimpieza)}</dd></div>
            <div><dt>Tarifa de servicio</dt><dd>{FORMATO_PESOS.format(cotizada.cotizacion.tarifaServicio)}</dd></div>
            {cotizada.cotizacion.serviciosAdicionales.map((adicional) => (
              <div key={adicional.id}><dt>{adicional.nombre}</dt><dd>{FORMATO_PESOS.format(adicional.costo)}</dd></div>
            ))}
            <div><dt>Descuentos</dt><dd>{FORMATO_PESOS.format(cotizada.cotizacion.descuento)}</dd></div>
            <div className={estilos.total}><dt>Total</dt><dd>{FORMATO_PESOS.format(cotizada.cotizacion.total)}</dd></div>
          </dl>
          {yaReservada ? (
            <div className={estilos.yaReservada}>
              <output>Ya reservaste estas fechas. Tu reserva está confirmada y pagada.</output>
              <Link to={`/huesped/reservas/${yaReservada.id}`} className={estilos.verReserva}>Ver mi reserva</Link>
            </div>
          ) : solicitada ? (
            <div className={estilos.yaReservada}>
              <output>
                {`Solicitud enviada. El anfitrión puede responder${solicitada.venceEn
                  ? ` hasta el ${formatearPlazo(solicitada.venceEn)}` : ' dentro de su plazo'}; si la aprueba, te avisaremos
                para que pagues y confirmes la reserva.`}
              </output>
              <Link to={`/huesped/reservas/${solicitada.id}`} className={estilos.verReserva}>Ver mi solicitud</Link>
            </div>
          ) : (
            <button type="button" className={estilos.reservar} onClick={confirmarReserva} disabled={reservando}>
              {reservando ? 'Confirmando…' : 'Confirmar reserva'}
            </button>
          )}
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
