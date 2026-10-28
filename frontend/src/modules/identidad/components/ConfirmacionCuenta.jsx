import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import estilos from './ConfirmacionCuenta.module.css';

/**
 * Confirmación en línea de una acción sobre la cuenta: advertencia, contraseña y dos botones.
 * Solo valida que la contraseña no esté vacía; la decisión la toma la API.
 */
export default function ConfirmacionCuenta({
  titulo, advertencia, textoConfirmar, enviando, error, alConfirmar, alCancelar,
}) {
  const [contrasena, setContrasena] = useState('');
  const [errorCampo, setErrorCampo] = useState(null);

  function enviar(evento) {
    evento.preventDefault();
    if (contrasena === '') {
      setErrorCampo('Escribe tu contraseña para confirmar.');
      return;
    }
    setErrorCampo(null);
    alConfirmar(contrasena);
  }

  return (
    <form className={estilos.confirmacion} aria-labelledby="confirmacion-titulo"
      aria-describedby="confirmacion-advertencia" onSubmit={enviar} noValidate>
      <h3 id="confirmacion-titulo" className={estilos.titulo}>{titulo}</h3>
      <p id="confirmacion-advertencia" className={estilos.advertencia}>{advertencia}</p>
      <Campo id="contrasena-confirmacion" etiqueta="Contraseña actual" type="password"
        autoComplete="current-password" value={contrasena} error={errorCampo ?? undefined}
        onChange={(evento) => setContrasena(evento.target.value)} />
      {error && <p role="alert" className={estilos.aviso}>{error}</p>}
      <div className={estilos.acciones}>
        <button type="submit" className={estilos.confirmar} disabled={enviando}>{textoConfirmar}</button>
        <button type="button" className={estilos.cancelar} onClick={alCancelar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  );
}

ConfirmacionCuenta.propTypes = {
  titulo: PropTypes.string.isRequired,
  advertencia: PropTypes.string.isRequired,
  textoConfirmar: PropTypes.string.isRequired,
  enviando: PropTypes.bool.isRequired,
  error: PropTypes.string,
  alConfirmar: PropTypes.func.isRequired,
  alCancelar: PropTypes.func.isRequired,
};
