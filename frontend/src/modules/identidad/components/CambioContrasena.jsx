import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import { validarCambioContrasena } from '../models/usuario.js';
import RequisitosContrasena from './RequisitosContrasena.jsx';
import estilos from './CambioContrasena.module.css';

const VACIO = { contrasenaActual: '', contrasenaNueva: '' };
const MENSAJE_ACTUAL = 'La contraseña actual no es correcta.';

/**
 * Formulario para cambiar la contraseña propia. `alCambiar` llama a la API y lanza el ErrorApi si falla.
 * Solo se ramifica por el código de negocio; el texto del detalle nunca decide nada.
 */
export default function CambioContrasena({ alCambiar }) {
  const [datos, setDatos] = useState(VACIO);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [cambiada, setCambiada] = useState(false);
  const [enviando, setEnviando] = useState(false);

  function cambiar(evento) {
    const { name, value } = evento.target;
    setDatos({ ...datos, [name]: value });
    setCambiada(false);
  }

  async function enviar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    setCambiada(false);
    const erroresFormato = validarCambioContrasena(datos);
    setErrores(erroresFormato);
    if (Object.keys(erroresFormato).length > 0) {
      return;
    }

    setEnviando(true);
    try {
      await alCambiar(datos);
      setDatos(VACIO);
      setCambiada(true);
    } catch (error) {
      if (error.codigo === 'CONFIRMACION_INVALIDA') {
        setErrores({ contrasenaActual: MENSAJE_ACTUAL });
      } else {
        setErrores(error.errores ?? {});
        setErrorGeneral(error.message);
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className={estilos.formulario} onSubmit={enviar} noValidate>
      {cambiada && <output className={estilos.exito}>Tu contraseña cambió.</output>}
      {errorGeneral && <p role="alert" className={estilos.aviso}>{errorGeneral}</p>}
      <Campo id="contrasenaActual" etiqueta="Contraseña actual" type="password" autoComplete="current-password"
        value={datos.contrasenaActual} onChange={cambiar} error={errores.contrasenaActual} />
      <div>
        <Campo id="contrasenaNueva" etiqueta="Contraseña nueva" type="password" autoComplete="new-password"
          value={datos.contrasenaNueva} onChange={cambiar} error={errores.contrasenaNueva} />
        <RequisitosContrasena contrasena={datos.contrasenaNueva} />
      </div>
      <button type="submit" className={estilos.boton} disabled={enviando}>
        {enviando ? 'Guardando…' : 'Cambiar contraseña'}
      </button>
    </form>
  );
}

CambioContrasena.propTypes = {
  alCambiar: PropTypes.func.isRequired,
};
