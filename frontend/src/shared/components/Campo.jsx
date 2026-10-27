import PropTypes from 'prop-types';
import estilos from './Campo.module.css';

/** Campo de formulario con etiqueta, ayuda y error asociados para lectores de pantalla. */
export default function Campo({ id, etiqueta, ayuda, error, type = 'text', ...control }) {
  const idDescripcion = `${id}-descripcion`;
  const descripcion = error ?? ayuda;

  return (
    <div className={estilos.campo}>
      <label className={estilos.etiqueta} htmlFor={id}>{etiqueta}</label>
      <input
        id={id}
        name={id}
        type={type}
        className={estilos.control}
        aria-invalid={error ? true : undefined}
        aria-describedby={descripcion ? idDescripcion : undefined}
        {...control}
      />
      {descripcion && (
        <p id={idDescripcion} className={error ? estilos.error : estilos.ayuda}>{descripcion}</p>
      )}
    </div>
  );
}

Campo.propTypes = {
  id: PropTypes.string.isRequired,
  etiqueta: PropTypes.string.isRequired,
  ayuda: PropTypes.string,
  error: PropTypes.string,
  type: PropTypes.string,
};
