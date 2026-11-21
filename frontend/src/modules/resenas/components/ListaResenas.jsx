import PropTypes from 'prop-types';
import estilos from './ListaResenas.module.css';

const FORMATO_FECHA = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });

/**
 * Presentación de las reseñas visibles de una publicación (SOF-81, HU-36): autor, calificación, fecha y
 * contenido, en el orden en que llegan. Sin reseñas lo indica en lugar de mostrar una sección vacía.
 * @param {{resenas: import('../models/resena.js').ResenaPublicada[]}} props
 */
export default function ListaResenas({ resenas }) {
  if (resenas.length === 0) {
    return <p>Esta publicación aún no tiene reseñas.</p>;
  }
  return (
    <ul className={estilos.lista}>
      {resenas.map((resena) => (
        <li key={resena.id} className={estilos.resena}>
          <p className={estilos.autor}>{resena.autor}</p>
          <p className={estilos.meta}>
            Calificación: {resena.calificacion} de 5 · {FORMATO_FECHA.format(new Date(resena.fecha))}
          </p>
          <p>{resena.contenido}</p>
        </li>
      ))}
    </ul>
  );
}

ListaResenas.propTypes = {
  resenas: PropTypes.arrayOf(PropTypes.shape({
    id: PropTypes.number.isRequired,
    reservaId: PropTypes.number.isRequired,
    autor: PropTypes.string.isRequired,
    calificacion: PropTypes.number.isRequired,
    contenido: PropTypes.string.isRequired,
    fecha: PropTypes.string.isRequired,
  })).isRequired,
};
