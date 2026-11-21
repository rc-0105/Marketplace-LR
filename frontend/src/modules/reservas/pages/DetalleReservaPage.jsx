import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import FormularioResena from '../../resenas/components/FormularioResena.jsx';
import {
  formatearFecha, formatearPesos, nombreDeEstadoPago, nombreDeEstadoReserva,
} from '../models/reserva.js';
import { consultarReserva } from '../services/reservaService.js';
import estilos from './DetalleReservaPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar la reserva. Intenta de nuevo.';
const SIN_ALOJAMIENTO = 'Alojamiento no disponible';
const SERVICIO_NO_DISPONIBLE = 'Servicio no disponible';

function Volver() {
  return <Link to="/huesped/reservas" className={estilos.volver}>Volver a mis reservas</Link>;
}

/**
 * Una reserva del huésped (SOF-35): el alojamiento, el desglose que quedó fijo al reservar, el estado actual
 * y el del pago. Los servicios adicionales solo guardan su total, así que se listan por nombre.
 */
export default function DetalleReservaPage() {
  const { id } = useParams();
  const [reserva, setReserva] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);

  useEffect(() => {
    let vigente = true;
    consultarReserva(id)
      .then((detalle) => vigente && setReserva(detalle))
      // Un 403 o un 404 traen su propio mensaje; sin respuesta de la API se muestra uno general.
      .catch((error) => vigente && setErrorCarga(error.estado ? error.message : MENSAJE_ERROR));
    return () => {
      vigente = false;
    };
  }, [id]);

  if (errorCarga) {
    return (
      <main className={estilos.pagina}>
        <p role="alert" className={estilos.error}>{errorCarga}</p>
        <Volver />
      </main>
    );
  }
  if (!reserva) {
    return <main className={estilos.pagina}><p className={estilos.nota}>Cargando la reserva…</p></main>;
  }

  const { alojamiento } = reserva;
  const noches = reserva.noches === 1 ? '1 noche' : `${reserva.noches} noches`;
  return (
    <main className={estilos.pagina}>
      <Volver />
      <div className={estilos.cabecera}>
        {alojamiento?.imagenPrincipal && (
          <img src={alojamiento.imagenPrincipal} alt={`Foto de ${alojamiento.titulo}`} className={estilos.foto} />
        )}
        <div className={estilos.texto}>
          <span className={`${estilos.estado} ${estilos[reserva.estado]}`}>{nombreDeEstadoReserva(reserva.estado)}</span>
          <h1 className={estilos.titulo}>{alojamiento?.titulo ?? SIN_ALOJAMIENTO}</h1>
          {alojamiento?.ciudad && <p className={estilos.detalle}>{alojamiento.ciudad}</p>}
          <p className={estilos.detalle}>
            {`${formatearFecha(reserva.llegada)} – ${formatearFecha(reserva.salida)} · ${noches}`}
          </p>
        </div>
      </div>

      <section className={estilos.bloque} aria-labelledby="pago-titulo">
        <h2 id="pago-titulo" className={estilos.subtitulo}>Pago</h2>
        <dl className={estilos.desglose} aria-label="Desglose del pago">
          <div><dt>Noches</dt><dd>{reserva.noches}</dd></div>
          <div><dt>Precio base</dt><dd>{formatearPesos(reserva.precioBase)}</dd></div>
          <div><dt>Tarifa de limpieza</dt><dd>{formatearPesos(reserva.tarifaLimpieza)}</dd></div>
          <div><dt>Tarifa de servicio</dt><dd>{formatearPesos(reserva.tarifaServicio)}</dd></div>
          {reserva.serviciosAdicionales.length > 0 && (
            <div><dt>Servicios adicionales</dt><dd>{formatearPesos(reserva.totalServiciosAdicionales)}</dd></div>
          )}
          <div><dt>Descuentos</dt><dd>{formatearPesos(reserva.descuento)}</dd></div>
          <div className={estilos.total}><dt>Total</dt><dd>{formatearPesos(reserva.total)}</dd></div>
        </dl>
        {reserva.serviciosAdicionales.length > 0 && (
          <ul className={estilos.servicios} aria-label="Servicios adicionales incluidos">
            {reserva.serviciosAdicionales.map((servicio) => (
              <li key={servicio.id}>{servicio.nombre ?? SERVICIO_NO_DISPONIBLE}</li>
            ))}
          </ul>
        )}
        <dl className={estilos.estadoPago}>
          <div><dt>Estado del pago</dt><dd>{nombreDeEstadoPago(reserva.estadoPago)}</dd></div>
          {reserva.transaccionId && <div><dt>Transacción</dt><dd>{reserva.transaccionId}</dd></div>}
        </dl>
      </section>

      {reserva.estado === 'COMPLETADA' && <FormularioResena reservaId={reserva.id} />}
    </main>
  );
}
