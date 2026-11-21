import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';
import { consultarResenas } from '../services/resenaService.js';
import ListaResenas from './ListaResenas.jsx';

const MENSAJE_ERROR = 'No pudimos cargar las reseñas.';

/**
 * Reseñas de una publicación (SOF-81, HU-36): carga las reseñas visibles y se las pasa a la lista. La pantalla
 * que lo contiene pone el título de la sección. Si la consulta falla avisa aquí, sin afectar al resto de esa
 * pantalla.
 * @param {{publicacionId: number | string}} props
 */
export default function ResenasPublicacion({ publicacionId }) {
  const [resenas, setResenas] = useState(null);
  const [fallo, setFallo] = useState(false);

  useEffect(() => {
    let vigente = true;
    setResenas(null);
    setFallo(false);
    consultarResenas(publicacionId)
      .then((datos) => {
        if (vigente) setResenas(datos.resenas);
      })
      .catch(() => {
        if (vigente) setFallo(true);
      });
    return () => { vigente = false; };
  }, [publicacionId]);

  return (
    <>
      {fallo && <p role="alert">{MENSAJE_ERROR}</p>}
      {!fallo && resenas === null && <p>Cargando reseñas…</p>}
      {resenas !== null && <ListaResenas resenas={resenas} />}
    </>
  );
}

ResenasPublicacion.propTypes = {
  publicacionId: PropTypes.number.isRequired,
};
