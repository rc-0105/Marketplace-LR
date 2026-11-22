import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { aVistaNotificacion, enlaceDeNotificacion } from '../models/notificacion.js';
import { descontarNoLeida, fijarNoLeidas } from '../services/contadorNotificaciones.js';
import { listarNotificaciones, marcarLeida } from '../services/notificacionesService.js';
import estilos from './NotificacionesPage.module.css';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar tus notificaciones. Intenta de nuevo.';
const MENSAJE_ERROR_LEER = 'No pudimos marcar la notificación como leída. Intenta de nuevo.';

// Ícono, color y categoría de cada tipo de notificación; un tipo desconocido usa la campana general.
const ESTILO_POR_TIPO = {
  RESERVA_CONFIRMADA: { categoria: 'Reserva', tono: 'tonoExito', trazo: 'M5 12.5l4.5 4.5L19 7' },
  RESERVA_RECHAZADA: { categoria: 'Reserva', tono: 'tonoError', trazo: 'M6 6l12 12M18 6L6 18' },
  RESERVA_CANCELADA: {
    categoria: 'Reserva', tono: 'tonoError',
    trazo: 'M7 3v3M17 3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM10 13l4 4M14 13l-4 4',
  },
  PUBLICACION_ESTADO: { categoria: 'Publicación', tono: 'tonoMarca', trazo: 'M4 11l8-7 8 7M6 9.5V20h12V9.5M10 20v-5h4v5' },
  CUENTA_RESTRINGIDA: { categoria: 'Cuenta', tono: 'tonoAviso', trazo: 'M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6zM12 8v4M12 15.5v.5' },
  SOLICITUD_RECIBIDA: { categoria: 'Solicitud', tono: 'tonoMarca', trazo: 'M4 6h16v12H4zM4 7l8 6 8-6' },
  SOLICITUD_APROBADA: { categoria: 'Solicitud', tono: 'tonoExito', trazo: 'M5 12.5l4.5 4.5L19 7' },
  SOLICITUD_RECHAZADA: { categoria: 'Solicitud', tono: 'tonoError', trazo: 'M6 6l12 12M18 6L6 18' },
  SOLICITUD_VENCIDA: { categoria: 'Solicitud', tono: 'tonoAviso', trazo: 'M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z' },
  RESENA_OCULTA: {
    categoria: 'Reseña', tono: 'tonoAviso',
    trazo: 'M3 3l18 18M10.6 6.1A9.8 9.8 0 0 1 12 6c5 0 9 6 9 6a16 16 0 0 1-2.6 3.2M6.6 7.6C4.3 9.2 3 12 3 12s4 6 9 6a8.6 8.6 0 0 0 4-1',
  },
};
const CAMPANA = 'M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0';
const ESTILO_GENERAL = { categoria: 'Aviso', tono: 'tonoMarca', trazo: CAMPANA };

function Icono({ trazo }) {
  return (
    <svg className={estilos.icono} viewBox="0 0 24 24" aria-hidden="true">
      <path d={trazo} />
    </svg>
  );
}

Icono.propTypes = { trazo: PropTypes.string.isRequired };

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
        setNotificaciones((respuesta?.notificaciones ?? []).map((notificacion) => (
          { ...aVistaNotificacion(notificacion), enlace: enlaceDeNotificacion(notificacion) })));
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

  const noLeidas = notificaciones ? notificaciones.filter((notificacion) => !notificacion.leida).length : 0;

  return (
    <main className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <div>
          <h1 className={estilos.titulo}>Notificaciones</h1>
          <p className={estilos.nota}>Lo que pasa con tus reservas, publicaciones y tu cuenta.</p>
        </div>
        {noLeidas > 0 && (
          <span className={estilos.contador}>{noLeidas === 1 ? '1 nueva' : `${noLeidas} nuevas`}</span>
        )}
      </header>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {errorLeer && <p role="alert" className={estilos.error}>{errorLeer}</p>}
      {!errorCarga && notificaciones === null && <p className={estilos.nota}>Cargando notificaciones…</p>}
      {notificaciones?.length === 0 && (
        <div className={estilos.vacio}>
          <span className={estilos.vacioIcono}><Icono trazo={CAMPANA} /></span>
          <p className={estilos.vacioTexto}>No tienes notificaciones.</p>
          <p className={estilos.nota}>Te avisaremos aquí cuando algo cambie.</p>
        </div>
      )}
      {notificaciones?.length > 0 && (
        <ul className={estilos.lista}>
          {notificaciones.map((notificacion) => {
            const estilo = ESTILO_POR_TIPO[notificacion.tipo] ?? ESTILO_GENERAL;
            const contenido = (
              <>
                <span className={`${estilos.avatar} ${estilos[estilo.tono]}`}><Icono trazo={estilo.trazo} /></span>
                <span className={estilos.cuerpo}>
                  <span className={estilos.categoria}>{estilo.categoria}</span>
                  <span className={estilos.mensaje}>{notificacion.mensaje}</span>
                  <span className={estilos.meta}>
                    {!notificacion.leida && <span className={estilos.etiqueta}>Sin leer</span>}
                    <time dateTime={notificacion.creadaEn}>{notificacion.fecha}</time>
                  </span>
                </span>
                {!notificacion.leida && <span className={estilos.punto} aria-hidden="true" />}
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
                {notificacion.enlace && (
                  <Link to={notificacion.enlace.ruta} className={estilos.enlace}>{notificacion.enlace.texto}</Link>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
