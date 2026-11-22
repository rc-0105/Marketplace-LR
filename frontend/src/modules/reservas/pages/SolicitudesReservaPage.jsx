import { useEffect, useState } from 'react';
import { formatearFecha, formatearPesos, formatearPlazo } from '../models/reserva.js';
import { listarSolicitudes } from '../services/reservaService.js';
import estilos from './SolicitudesReservaPage.module.css';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar las solicitudes. Intenta de nuevo.';
// Un 4xx trae un mensaje pensado para el usuario; sin respuesta de la API se muestra el general.
function mensajeDe(error, general) {
  return error?.estado >= 400 && error.estado < 500 ? error.message : general;
}

/**
 * Solicitudes de reserva que esperan la respuesta del anfitrión (SOF-79, HU-34): las más urgentes primero,
 * con el alojamiento, el huésped, las fechas, el total y el plazo para responder.
 */
export default function SolicitudesReservaPage() {
  const [solicitudes, setSolicitudes] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);

  useEffect(() => {
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    let vigente = true;
    listarSolicitudes()
      .then((lista) => vigente && setSolicitudes(lista))
      .catch((error) => vigente && setErrorCarga(mensajeDe(error, MENSAJE_ERROR_CARGA)));
    return () => {
      vigente = false;
    };
  }, []);

  return (
    <main className={estilos.pagina}>
      <header>
        <h1 className={estilos.titulo}>Solicitudes de reserva</h1>
        <p className={estilos.nota}>Responde antes del plazo o la solicitud vence y se libera el calendario.</p>
      </header>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && solicitudes === null && <p className={estilos.nota}>Cargando solicitudes…</p>}
      {solicitudes?.length === 0 && (
        <div className={estilos.vacio}>
          <p className={estilos.vacioTexto}>No tienes solicitudes pendientes.</p>
          <p className={estilos.nota}>Cuando un huésped pida reservar con aprobación, aparecerá aquí.</p>
        </div>
      )}
      {solicitudes?.length > 0 && (
        <ul className={estilos.lista} aria-label="Solicitudes de reserva">
          {solicitudes.map((solicitud) => {
            const noches = solicitud.noches === 1 ? '1 noche' : `${solicitud.noches} noches`;
            return (
              <li key={solicitud.id} className={estilos.item}>
                {solicitud.imagenPrincipal && (
                  <img src={solicitud.imagenPrincipal} alt="" className={estilos.foto} />
                )}
                <div className={estilos.cuerpo}>
                  <h2 className={estilos.alojamiento}>{solicitud.titulo ?? 'Alojamiento no disponible'}</h2>
                  {solicitud.ciudad && <p className={estilos.detalle}>{solicitud.ciudad}</p>}
                  <p className={estilos.detalle}>
                    {`Huésped: ${solicitud.huespedNombre ?? 'Sin nombre'}`}
                  </p>
                  <p className={estilos.detalle}>
                    {`${formatearFecha(solicitud.llegada)} – ${formatearFecha(solicitud.salida)} · ${noches}`}
                  </p>
                  <p className={estilos.total}>{formatearPesos(solicitud.total)}</p>
                  <p className={estilos.plazo}>
                    {`Responde antes del ${formatearPlazo(solicitud.venceEn)}`}
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
