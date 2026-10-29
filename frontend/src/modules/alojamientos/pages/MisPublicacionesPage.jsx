import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { nombreDeEstado } from '../models/publicacion.js';
import { listarMisPublicaciones } from '../services/alojamientosService.js';
import estilos from './MisPublicacionesPage.module.css';

const MENSAJE_ERROR = 'No pudimos cargar tus publicaciones. Intenta de nuevo.';

function resumenDe(publicacion) {
  const faltan = publicacion.camposFaltantes.length;
  if (faltan === 0) {
    return 'Tiene todos los datos para activarse.';
  }
  return faltan === 1 ? 'Le falta 1 dato para activarse.' : `Le faltan ${faltan} datos para activarse.`;
}

export default function MisPublicacionesPage() {
  const [publicaciones, setPublicaciones] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let vigente = true;
    listarMisPublicaciones()
      .then((lista) => vigente && setPublicaciones(lista))
      .catch(() => vigente && setError(MENSAJE_ERROR));
    // Si la página se desmonta antes de la respuesta, no se toca el estado.
    return () => {
      vigente = false;
    };
  }, []);

  return (
    <main className={estilos.pagina}>
      <div className={estilos.cabecera}>
        <h1 className={estilos.titulo}>Mis publicaciones</h1>
        <Link to="/anfitrion/publicaciones/nueva" className={estilos.nueva}>Nueva publicación</Link>
      </div>
      {error && <p role="alert" className={estilos.error}>{error}</p>}
      {!error && publicaciones === null && <p className={estilos.nota}>Cargando tus publicaciones…</p>}
      {publicaciones?.length === 0 && (
        <p className={estilos.nota}>Todavía no tienes publicaciones.</p>
      )}
      {publicaciones?.length > 0 && (
        <ul className={estilos.lista}>
          {publicaciones.map((publicacion) => (
            <li key={publicacion.id} className={estilos.tarjeta}>
              <h2 className={estilos.nombre}>{publicacion.titulo}</h2>
              <p className={estilos.detalle}>
                {nombreDeEstado(publicacion.estado)}
                {publicacion.ciudad && ` · ${publicacion.ciudad}`}
              </p>
              <p className={estilos.detalle}>{resumenDe(publicacion)}</p>
              <Link to={`/anfitrion/publicaciones/${publicacion.id}/editar`} className={estilos.editar}
                aria-label={`Editar ${publicacion.titulo}`}>
                Editar
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
