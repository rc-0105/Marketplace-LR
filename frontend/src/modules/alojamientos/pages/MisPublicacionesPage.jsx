import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { nombreDeCampo, nombreDeEstado } from '../models/publicacion.js';
import { activarPublicacion, listarMisPublicaciones, pausarPublicacion } from '../services/alojamientosService.js';
import estilos from './MisPublicacionesPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar tus publicaciones. Intenta de nuevo.';
const MENSAJE_CAMBIO_FALLIDO = 'No pudimos cambiar el estado. Intenta de nuevo.';

// La API decide si el cambio procede; aquí solo se elige qué botón ofrecer.
const ACCIONES = {
  BORRADOR: { etiqueta: 'Activar', ejecutar: activarPublicacion },
  PAUSADA: { etiqueta: 'Reactivar', ejecutar: activarPublicacion },
  ACTIVA: { etiqueta: 'Pausar', ejecutar: pausarPublicacion },
};

const RESUMEN_ESTADO = {
  PENDIENTE_REVISION: 'Esperando la aprobación de un administrador.',
  ACTIVA: 'Visible en las búsquedas.',
  PAUSADA: 'Fuera de las búsquedas hasta que la reactives.',
};

function resumenDe(publicacion) {
  if (RESUMEN_ESTADO[publicacion.estado]) {
    return RESUMEN_ESTADO[publicacion.estado];
  }
  const faltan = publicacion.camposFaltantes.length;
  if (faltan === 0) {
    return 'Tiene todos los datos para activarse.';
  }
  return faltan === 1 ? 'Le falta 1 dato para activarse.' : `Le faltan ${faltan} datos para activarse.`;
}

// En un 422 los campos que faltan son los mismos que la API ya reportó en camposFaltantes.
function mensajeDe(error, publicacion) {
  if (error.estado === 422 && publicacion.camposFaltantes.length > 0) {
    return `${error.message} Completa: ${publicacion.camposFaltantes.map(nombreDeCampo).join(', ')}.`;
  }
  return error.estado ? error.message : MENSAJE_CAMBIO_FALLIDO;
}

export default function MisPublicacionesPage() {
  const [publicaciones, setPublicaciones] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [errores, setErrores] = useState({});
  const [enCurso, setEnCurso] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarMisPublicaciones()
      .then((lista) => vigente && setPublicaciones(lista))
      .catch(() => vigente && setErrorCarga(MENSAJE_ERROR));
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, []);

  async function cambiarEstado(publicacion, accion) {
    setEnCurso(publicacion.id);
    setErrores((actuales) => ({ ...actuales, [publicacion.id]: null }));
    try {
      const actualizada = await accion.ejecutar(publicacion.id);
      setPublicaciones((lista) => lista.map((otra) => (otra.id === actualizada.id ? actualizada : otra)));
    } catch (error) {
      setErrores((actuales) => ({ ...actuales, [publicacion.id]: mensajeDe(error, publicacion) }));
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <main className={estilos.pagina}>
      <div className={estilos.cabecera}>
        <h1 className={estilos.titulo}>Mis publicaciones</h1>
        <Link to="/anfitrion/publicaciones/nueva" className={estilos.nueva}>Nueva publicación</Link>
      </div>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && publicaciones === null && <p className={estilos.nota}>Cargando tus publicaciones…</p>}
      {publicaciones?.length === 0 && (
        <p className={estilos.nota}>Todavía no tienes publicaciones.</p>
      )}
      {publicaciones?.length > 0 && (
        <ul className={estilos.lista}>
          {publicaciones.map((publicacion) => {
            const accion = ACCIONES[publicacion.estado];
            return (
              <li key={publicacion.id} className={estilos.tarjeta}>
                <h2 className={estilos.nombre}>{publicacion.titulo}</h2>
                <p className={estilos.detalle}>
                  {nombreDeEstado(publicacion.estado)}
                  {publicacion.ciudad && ` · ${publicacion.ciudad}`}
                </p>
                <p className={estilos.detalle}>{resumenDe(publicacion)}</p>
                {errores[publicacion.id] && (
                  <p role="alert" className={estilos.error}>{errores[publicacion.id]}</p>
                )}
                <div className={estilos.acciones}>
                  <Link to={`/anfitrion/publicaciones/${publicacion.id}/editar`} className={estilos.editar}
                    aria-label={`Editar ${publicacion.titulo}`}>
                    Editar
                  </Link>
                  {accion && (
                    <button type="button" className={estilos.cambiarEstado}
                      aria-label={`${accion.etiqueta} ${publicacion.titulo}`}
                      disabled={enCurso === publicacion.id}
                      onClick={() => cambiarEstado(publicacion, accion)}>
                      {accion.etiqueta}
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
