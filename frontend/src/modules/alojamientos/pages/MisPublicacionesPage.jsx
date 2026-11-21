import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { nombreDeCampo, nombreDeEstado } from '../models/publicacion.js';
import { activarPublicacion, listarMisPublicaciones, pausarPublicacion } from '../services/alojamientosService.js';
import estilos from './MisPublicacionesPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar tus publicaciones. Intenta de nuevo.';
const MENSAJE_CAMBIO_FALLIDO = 'No pudimos cambiar el estado. Intenta de nuevo.';
const FORMATO_PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

// La API decide si el cambio procede; aquí solo se elige qué botón ofrecer.
const ACCIONES = {
  BORRADOR: { etiqueta: 'Solicitar activación', ejecutar: activarPublicacion },
  PAUSADA: { etiqueta: 'Reactivar', ejecutar: activarPublicacion },
  ACTIVA: { etiqueta: 'Pausar', ejecutar: pausarPublicacion },
};

// Las columnas del tablero, en el orden del ciclo de vida de una publicación. La de bloqueadas solo aparece
// si el anfitrión tiene alguna, para no sumar una columna vacía al tablero de todos.
const COLUMNAS = [
  { estado: 'BORRADOR', titulo: 'Borrador' },
  { estado: 'PENDIENTE_REVISION', titulo: 'Pendiente de revisión' },
  { estado: 'ACTIVA', titulo: 'Activa' },
  { estado: 'PAUSADA', titulo: 'Pausada' },
  { estado: 'BLOQUEADA', titulo: 'Bloqueada', soloConPublicaciones: true },
];

const RESUMEN_ESTADO = {
  PENDIENTE_REVISION: 'Esperando la aprobación de un administrador.',
  ACTIVA: 'Visible en las búsquedas.',
  PAUSADA: 'Fuera de las búsquedas hasta que la reactives.',
  BLOQUEADA: 'Fuera de las búsquedas y sin reservas nuevas.',
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

  function tarjetaDe(publicacion) {
    const accion = ACCIONES[publicacion.estado];
    const foto = publicacion.imagenes?.[0];
    const lugar = [publicacion.tipo?.nombre, publicacion.ciudad].filter(Boolean).join(' · ');
    return (
      <li key={publicacion.id} className={estilos.tarjeta}>
        {foto ? (
          <img src={foto} alt={`Foto de ${publicacion.titulo}`} className={estilos.foto} />
        ) : (
          <div className={estilos.sinFoto}>Sin foto</div>
        )}
        <span className={`${estilos.estado} ${estilos[publicacion.estado]}`}>
          {nombreDeEstado(publicacion.estado)}
        </span>
        <div className={estilos.texto}>
          <h3 className={estilos.nombre}>{publicacion.titulo}</h3>
          {lugar && <p className={estilos.detalle}>{lugar}</p>}
        </div>
        {publicacion.precioNoche != null && (
          <p className={estilos.precio}>
            {FORMATO_PESOS.format(publicacion.precioNoche)} <span>por noche</span>
          </p>
        )}
        <p className={estilos.detalle}>{resumenDe(publicacion)}</p>
        {publicacion.estado === 'BORRADOR' && publicacion.observacionesRevision && (
          <p className={estilos.observaciones}>
            Devuelta por el administrador: {publicacion.observacionesRevision}
          </p>
        )}
        {publicacion.estado === 'BLOQUEADA' && (
          <div className={estilos.observaciones}>
            {publicacion.motivoBloqueo && (
              <p className={estilos.parrafo}>Bloqueada por un administrador: {publicacion.motivoBloqueo}</p>
            )}
            <p className={estilos.parrafo}>Contacta a soporte para revisarla.</p>
          </div>
        )}
        {errores[publicacion.id] && (
          <p role="alert" className={estilos.error}>{errores[publicacion.id]}</p>
        )}
        <div className={estilos.acciones}>
          <Link to={`/anfitrion/publicaciones/${publicacion.id}/editar`} className={estilos.enlace}
            aria-label={`Editar ${publicacion.titulo}`}>
            {publicacion.estado === 'BORRADOR' ? 'Completar' : 'Editar'}
          </Link>
          <Link to={`/anfitrion/publicaciones/${publicacion.id}/disponibilidad`} className={estilos.enlace}
            aria-label={`Disponibilidad de ${publicacion.titulo}`}>
            Disponibilidad
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
  }

  return (
    <main className={estilos.pagina}>
      <div className={estilos.cabecera}>
        <div>
          <h1 className={estilos.titulo}>Mis publicaciones</h1>
          <p className={estilos.subtitulo}>Administra tus alojamientos, su estado y su disponibilidad.</p>
        </div>
        <Link to="/anfitrion/publicaciones/nueva" className={estilos.nueva}>
          <span aria-hidden="true">+</span> Nueva publicación
        </Link>
      </div>
      {errorCarga && <p role="alert" className={estilos.error}>{errorCarga}</p>}
      {!errorCarga && publicaciones === null && <p className={estilos.nota}>Cargando tus publicaciones…</p>}
      {publicaciones?.length === 0 && (
        <p className={estilos.nota}>Todavía no tienes publicaciones.</p>
      )}
      {publicaciones?.length > 0 && (
        <>
          <p className={estilos.total}>Todas · {publicaciones.length}</p>
          <div className={estilos.tablero}>
            {COLUMNAS.map((columna) => {
              const deLaColumna = publicaciones.filter((publicacion) => publicacion.estado === columna.estado);
              if (columna.soloConPublicaciones && deLaColumna.length === 0) {
                return null;
              }
              return (
                <section key={columna.estado} className={estilos.columna} aria-label={columna.titulo}>
                  <div className={estilos.encabezadoColumna}>
                    <h2 className={estilos.tituloColumna}>{columna.titulo}</h2>
                    <span className={estilos.conteo}>{deLaColumna.length}</span>
                  </div>
                  {deLaColumna.length === 0 && <p className={estilos.columnaVacia}>Sin publicaciones aquí</p>}
                  <ul className={estilos.lista}>{deLaColumna.map(tarjetaDe)}</ul>
                </section>
              );
            })}
          </div>
          <p className={estilos.leyenda}>
            Las publicaciones pasan por Borrador, En revisión, Activa y Pausada. Solo las activas aparecen en las
            búsquedas y reciben reservas. Si un administrador bloquea una publicación, queda Bloqueada hasta que
            soporte la revise.
          </p>
        </>
      )}
    </main>
  );
}
