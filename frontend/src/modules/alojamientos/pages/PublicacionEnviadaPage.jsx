import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { nombreDeEstado } from '../models/publicacion.js';
import { consultarPublicacion } from '../services/alojamientosService.js';
import estilos from './PublicacionEnviadaPage.module.css';

/** Confirma que la publicación quedó pendiente de revisión: un administrador la revisa antes de activarla. */
export default function PublicacionEnviadaPage() {
  const { id } = useParams();
  const [publicacion, setPublicacion] = useState(null);
  const [fallo, setFallo] = useState(false);

  useEffect(() => {
    let vigente = true;
    consultarPublicacion(id)
      .then((cargada) => vigente && setPublicacion(cargada))
      .catch(() => vigente && setFallo(true));
    return () => {
      vigente = false;
    };
  }, [id]);

  return (
    <main className={estilos.pagina}>
      <div className={estilos.marca} aria-hidden="true">✓</div>
      <h1 className={estilos.titulo}>Publicación enviada a revisión</h1>
      <p className={estilos.texto}>
        Un administrador revisará tu publicación antes de que aparezca en las búsquedas.
      </p>
      {fallo && <p role="alert" className={estilos.error}>No pudimos cargar los datos de la publicación.</p>}
      {publicacion && (
        <dl className={estilos.resumen}>
          <div className={estilos.fila}>
            <dt>Publicación</dt>
            <dd>{publicacion.titulo}</dd>
          </div>
          <div className={estilos.fila}>
            <dt>Estado</dt>
            <dd><span className={estilos.estado}>{nombreDeEstado(publicacion.estado)}</span></dd>
          </div>
        </dl>
      )}
      <Link to="/anfitrion/publicaciones" className={estilos.volver}>Volver a mis publicaciones</Link>
    </main>
  );
}
