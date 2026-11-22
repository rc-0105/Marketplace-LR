import { useCallback, useEffect, useState } from 'react';
import RechazarSolicitudDialogo from '../components/RechazarSolicitudDialogo.jsx';
import { formatearFecha, formatearPesos, formatearPlazo } from '../models/reserva.js';
import { aprobarSolicitud, listarSolicitudes, rechazarSolicitud } from '../services/reservaService.js';
import estilos from './SolicitudesReservaPage.module.css';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar las solicitudes. Intenta de nuevo.';
const MENSAJE_ERROR_RESPUESTA = 'No pudimos registrar tu respuesta. Intenta de nuevo.';
// Un 4xx trae un mensaje pensado para el usuario; sin respuesta de la API se muestra el general.
function mensajeDe(error, general) {
  return error?.estado >= 400 && error.estado < 500 ? error.message : general;
}

/**
 * Solicitudes de reserva que esperan la respuesta del anfitrión (SOF-79, HU-34): las más urgentes primero.
 * Aprobar abre la ventana de pago del huésped; rechazar exige un motivo. Si la solicitud ya no se puede
 * responder (409: venció o ya se respondió) se muestra el aviso y se recarga la lista.
 */
export default function SolicitudesReservaPage() {
  const [solicitudes, setSolicitudes] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [errorRespuesta, setErrorRespuesta] = useState(null);
  const [respondiendo, setRespondiendo] = useState(null);
  const [rechazando, setRechazando] = useState(null);

  const cargar = useCallback(() => listarSolicitudes()
    .then((lista) => {
      setSolicitudes(lista);
      setErrorCarga(null);
    })
    .catch((error) => setErrorCarga(mensajeDe(error, MENSAJE_ERROR_CARGA))), []);

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

  function quitar(id) {
    setSolicitudes((actuales) => actuales.filter((solicitud) => solicitud.id !== id));
  }

  // Un 409 significa que la solicitud ya no es respondible: se avisa y se vuelve a pedir la lista real.
  function alFallar(error) {
    setErrorRespuesta(mensajeDe(error, MENSAJE_ERROR_RESPUESTA));
    if (error?.estado === 409) cargar();
  }

  async function aprobar(solicitud) {
    setAviso(null);
    setErrorRespuesta(null);
    setRespondiendo(solicitud.id);
    try {
      const resolucion = await aprobarSolicitud(solicitud.id);
      quitar(solicitud.id);
      const plazo = resolucion?.venceEn ? ` hasta el ${formatearPlazo(resolucion.venceEn)}` : ' dentro de su plazo';
      setAviso(`Solicitud aprobada. El huésped puede pagar y confirmar la reserva${plazo}.`);
    } catch (error) {
      alFallar(error);
    } finally {
      setRespondiendo(null);
    }
  }

  async function rechazar(solicitud, motivo) {
    setAviso(null);
    setErrorRespuesta(null);
    try {
      await rechazarSolicitud(solicitud.id, motivo);
    } catch (error) {
      // Un 400 es un motivo inválido: se corrige dentro del diálogo. Cualquier otro caso lo cierra.
      if (error?.estado === 400) throw error;
      setRechazando(null);
      alFallar(error);
      return;
    }
    setRechazando(null);
    quitar(solicitud.id);
    setAviso('Solicitud rechazada. Le avisamos al huésped.');
  }

  return (
    <main className={estilos.pagina}>
      <header>
        <h1 className={estilos.titulo}>Solicitudes de reserva</h1>
        <p className={estilos.nota}>Responde antes del plazo o la solicitud vence y se libera el calendario.</p>
      </header>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {errorRespuesta && <p role="alert" className={estilos.error}>{errorRespuesta}</p>}
      {aviso && <output className={estilos.exito}>{aviso}</output>}
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
                <div className={estilos.acciones}>
                  <button type="button" className={estilos.aprobar} disabled={respondiendo === solicitud.id}
                    onClick={() => aprobar(solicitud)}>
                    {respondiendo === solicitud.id ? 'Aprobando…' : 'Aprobar'}
                  </button>
                  <button type="button" className={estilos.rechazar} disabled={respondiendo === solicitud.id}
                    onClick={() => setRechazando(solicitud)}>
                    Rechazar
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {rechazando && (
        <RechazarSolicitudDialogo titulo={rechazando.titulo ?? 'alojamiento'}
          alConfirmar={(motivo) => rechazar(rechazando, motivo)} alCancelar={() => setRechazando(null)} />
      )}
    </main>
  );
}
