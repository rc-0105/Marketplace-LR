import { useState } from 'react';
import PropTypes from 'prop-types';
import estilos from './Campo.module.css';

/**
 * Campo de formulario con etiqueta, ayuda y error asociados para lectores de pantalla.
 * Si es de contraseña, ofrece un botón para mostrar u ocultar lo escrito.
 */
export default function Campo({ id, etiqueta, ayuda, error, type = 'text', ...control }) {
  const [contrasenaVisible, setContrasenaVisible] = useState(false);
  const idDescripcion = `${id}-descripcion`;
  const descripcion = error ?? ayuda;
  const esContrasena = type === 'password';

  const entrada = (
    <input
      id={id}
      name={id}
      type={esContrasena && contrasenaVisible ? 'text' : type}
      className={estilos.control}
      aria-invalid={error ? true : undefined}
      aria-describedby={descripcion ? idDescripcion : undefined}
      {...control}
    />
  );

  return (
    <div className={estilos.campo}>
      <label className={estilos.etiqueta} htmlFor={id}>{etiqueta}</label>
      {esContrasena ? (
        <div className={estilos.conBoton}>
          {entrada}
          <button
            type="button"
            className={estilos.alternar}
            aria-controls={id}
            aria-label={contrasenaVisible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            onClick={() => setContrasenaVisible((visible) => !visible)}
          >
            {contrasenaVisible ? 'Ocultar' : 'Mostrar'}
          </button>
        </div>
      ) : entrada}
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
