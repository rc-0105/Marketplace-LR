import { useEffect, useState } from 'react';
import { aVistaNotificacion } from '../models/notificacion.js';
import { descontarNoLeida, fijarNoLeidas } from '../services/contadorNotificaciones.js';
import { listarNotificaciones, marcarLeida } from '../services/notificacionesService.js';
import estilos from './NotificacionesPage.module.css';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar tus notificaciones. Intenta de nuevo.';
const MENSAJE_ERROR_LEER = 'No pudimos marcar la notificación como leída. Intenta de nuevo.';

function mensajeDe(error, generico) {
  return error?.estado ? error.message : generico;
}

/**
 * Notificaciones del usuario de la sesión, de la más reciente a la más antigua. Abrir una sin leer
 * (pulsarla) la marca como leída y baja el contador compartido con la campana del encabezado;
 * las ya leídas no llaman a la API.
 */
export default function NotificacionesPage() {
  const [notificaciones, setNotificaciones] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [errorLeer, setErrorLeer] = useState(null);
  const [abriendo, setAbriendo] = useState(null);

  useEffect(() => {
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    let vigente = true;
    listarNotificaciones()
      .then((respuesta) => {
        if (!vigente) return;
        setNotificaciones((respuesta?.notificaciones ?? []).map(aVistaNotificacion));
        fijarNoLeidas(respuesta?.noLeidas);
      })
      .catch((error) => vigente && setErrorCarga(mensajeDe(error, MENSAJE_ERROR_CARGA)));
    return () => {
      vigente = false;
    };
  }, []);

  async function abrir(notificacion) {
    if (notificacion.leida || abriendo !== null) return;
    setAbriendo(notificacion.id);
    setErrorLeer(null);
    try {
      const leida = aVistaNotificacion(await marcarLeida(notificacion.id));
      setNotificaciones((actuales) => actuales.map((actual) => (
        actual.id === notificacion.id ? { ...actual, ...leida, leida: true } : actual)));
      descontarNoLeida();
    } catch (error) {
      setErrorLeer(mensajeDe(error, MENSAJE_ERROR_LEER));
    } finally {
      setAbriendo(null);
    }
  }

  return (
    <main className={estilos.pagina}>
      <h1 className={estilos.titulo}>Notificaciones</h1>
      <p className={estilos.nota}>Lo que pasa con tus reservas, publicaciones y tu cuenta.</p>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {errorLeer && <p role="alert" className={estilos.error}>{errorLeer}</p>}
      {!errorCarga && notificaciones === null && <p className={estilos.nota}>Cargando notificaciones…</p>}
      {notificaciones?.length === 0 && <p className={estilos.nota}>No tienes notificaciones.</p>}
      {notificaciones?.length > 0 && (
        <ul className={estilos.lista}>
          {notificaciones.map((notificacion) => {
            const contenido = (
              <>
                <span className={estilos.mensaje}>{notificacion.mensaje}</span>
                <span className={estilos.meta}>
                  {!notificacion.leida && <span className={estilos.etiqueta}>Sin leer</span>}
                  <time dateTime={notificacion.creadaEn}>{notificacion.fecha}</time>
                </span>
              </>
            );
            return (
              <li key={notificacion.id}
                className={notificacion.leida ? estilos.item : `${estilos.item} ${estilos.noLeida}`}>
                {notificacion.leida ? (
                  <div className={estilos.contenido}>{contenido}</div>
                ) : (
                  <button type="button" className={estilos.abrir} disabled={abriendo === notificacion.id}
                    onClick={() => abrir(notificacion)}>
                    {contenido}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
