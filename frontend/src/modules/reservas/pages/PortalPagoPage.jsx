import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import PagoReserva from '../components/PagoReserva.jsx';
import { formatearFecha, formatearPesos, nombreDeEstadoReserva } from '../models/reserva.js';
import { consultarReserva } from '../services/reservaService.js';
import estilos from './PortalPagoPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar la reserva. Intenta de nuevo.';
const SIN_ALOJAMIENTO = 'Alojamiento no disponible';
// El apartado vence a una hora concreta del día: se muestra en hora de Colombia.
const FORMATO_HORA = new Intl.DateTimeFormat('es-CO', {
  hour: 'numeric', minute: '2-digit', timeZone: 'America/Bogota',
});

/**
 * Portal de pagos de una reserva PENDIENTE (SOF-34): al confirmar la reserva el huésped llega aquí, ve el
 * resumen de lo que paga y elige el método de pago. Una reserva que ya no está pendiente no se puede pagar:
 * se informa su estado y se ofrece ir a su detalle.
 */
export default function PortalPagoPage() {
  const { id } = useParams();
  const [reserva, setReserva] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [pagada, setPagada] = useState(false);

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

  const enlaceDetalle = <Link to={`/huesped/reservas/${id}`} className={estilos.enlace}>Ver mi reserva</Link>;

  if (errorCarga) {
    return (
      <main className={estilos.pagina}>
        <p role="alert" className={estilos.error}>{errorCarga}</p>
        <Link to="/huesped/reservas" className={estilos.enlace}>Ir a mis reservas</Link>
      </main>
    );
  }
  if (!reserva) {
    return <main className={estilos.pagina}><p className={estilos.nota}>Cargando el portal de pagos…</p></main>;
  }

  const { alojamiento } = reserva;
  const noches = reserva.noches === 1 ? '1 noche' : `${reserva.noches} noches`;
  const pendiente = reserva.estado === 'PENDIENTE';
  return (
    <main className={estilos.pagina}>
      <header className={estilos.encabezado}>
        <p className={estilos.paso}>Último paso</p>
        <h1 className={estilos.titulo}>Portal de pagos</h1>
        <p className={estilos.nota}>Revisa tu reserva y elige cómo pagarla.</p>
      </header>

      <div className={estilos.columnas}>
        <section className={estilos.resumen} aria-labelledby="resumen-titulo">
          {alojamiento?.imagenPrincipal && (
            <img src={alojamiento.imagenPrincipal} alt={`Foto de ${alojamiento.titulo}`} className={estilos.foto} />
          )}
          <div className={estilos.cabeceraAlojamiento}>
            <h2 id="resumen-titulo" className={estilos.alojamiento}>{alojamiento?.titulo ?? SIN_ALOJAMIENTO}</h2>
            {alojamiento?.ciudad && <p className={estilos.nota}>{alojamiento.ciudad}</p>}
          </div>
          <div className={estilos.fechas}>
            <div className={estilos.fecha}>
              <span className={estilos.fechaEtiqueta}>Llegada</span>
              <span>{formatearFecha(reserva.llegada)}</span>
            </div>
            <div className={estilos.fecha}>
              <span className={estilos.fechaEtiqueta}>Salida</span>
              <span>{formatearFecha(reserva.salida)}</span>
            </div>
            <div className={estilos.fecha}>
              <span className={estilos.fechaEtiqueta}>Estancia</span>
              <span>{noches}</span>
            </div>
          </div>
          <dl className={estilos.desglose} aria-label="Resumen del pago">
            <div><dt>Precio base</dt><dd>{formatearPesos(reserva.precioBase)}</dd></div>
            <div><dt>Tarifa de limpieza</dt><dd>{formatearPesos(reserva.tarifaLimpieza)}</dd></div>
            <div><dt>Tarifa de servicio</dt><dd>{formatearPesos(reserva.tarifaServicio)}</dd></div>
            {reserva.serviciosAdicionales.length > 0 && (
              <div><dt>Servicios adicionales</dt><dd>{formatearPesos(reserva.totalServiciosAdicionales)}</dd></div>
            )}
            <div><dt>Descuentos</dt><dd>{formatearPesos(reserva.descuento)}</dd></div>
            <div className={estilos.total}><dt>Total a pagar</dt><dd>{formatearPesos(reserva.total)}</dd></div>
          </dl>
        </section>

        <section className={estilos.pasarela} aria-labelledby="pasarela-titulo">
          <div className={estilos.cabeceraPasarela}>
            <h2 id="pasarela-titulo" className={estilos.subtitulo}>Pago seguro</h2>
            <span className={estilos.sello}>Modo prueba</span>
          </div>
          {pendiente ? (
            <>
              {reserva.venceEn && !pagada && (
                <p className={estilos.apartado}>
                  <span className={estilos.punto} aria-hidden="true" />
                  {`Tus noches están apartadas hasta las ${FORMATO_HORA.format(new Date(reserva.venceEn))}.`}
                </p>
              )}
              <PagoReserva reservaId={reserva.id} total={reserva.total} onAprobado={() => setPagada(true)} />
            </>
          ) : (
            <p role="status" className={estilos.aviso}>
              {`Esta reserva ya no está pendiente de pago: su estado es ${nombreDeEstadoReserva(reserva.estado).toLowerCase()}.`}
            </p>
          )}
          {enlaceDetalle}
        </section>
      </div>
    </main>
  );
}
