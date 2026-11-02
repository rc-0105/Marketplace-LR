import { useState } from 'react';
import PropTypes from 'prop-types';
import Campo from '../../../shared/components/Campo.jsx';
import estilos from './FormularioPeriodo.module.css';

const VACIO = { fechaInicio: '', fechaFin: '' };
const MENSAJE_FALLA = 'No pudimos guardar el periodo. Intenta de nuevo.';

// Solo el formato: que las fechas lleguen. Si son válidas lo decide la API.
function camposVacios(fechas) {
  const errores = {};
  if (!fechas.fechaInicio) {
    errores.fechaInicio = 'Elige la fecha inicial.';
  }
  if (!fechas.fechaFin) {
    errores.fechaFin = 'Elige la fecha final.';
  }
  return errores;
}

/**
 * Fechas de un periodo disponible, para registrarlo o modificarlo. Muestra los
 * errores que devuelve la API, como un cruce con otro periodo o una reserva confirmada.
 */
export default function FormularioPeriodo({ id, titulo, inicial, etiquetaEnviar, alGuardar, alCancelar }) {
  const [fechas, setFechas] = useState(inicial ?? VACIO);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState(null);
  const [guardando, setGuardando] = useState(false);

  function cambiar(evento) {
    const { name, value } = evento.target;
    const campo = name.replace(`${id}-`, '');
    setFechas((actuales) => ({ ...actuales, [campo]: value }));
  }

  async function enviar(evento) {
    evento.preventDefault();
    const vacios = camposVacios(fechas);
    setErrores(vacios);
    setErrorGeneral(null);
    if (Object.keys(vacios).length > 0) {
      return;
    }
    setGuardando(true);
    try {
      await alGuardar(fechas);
      if (!inicial) {
        setFechas(VACIO);
      }
    } catch (error) {
      setErrores(error.errores ?? {});
      setErrorGeneral(error.estado ? error.message : MENSAJE_FALLA);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form className={estilos.formulario} aria-label={titulo} noValidate onSubmit={enviar}>
      <div className={estilos.campos}>
        <Campo id={`${id}-fechaInicio`} etiqueta="Fecha inicial" type="date" value={fechas.fechaInicio}
          error={errores.fechaInicio} onChange={cambiar} />
        <Campo id={`${id}-fechaFin`} etiqueta="Fecha final" type="date" value={fechas.fechaFin}
          ayuda="Día de salida: no cuenta como noche disponible." error={errores.fechaFin} onChange={cambiar} />
      </div>
      {errorGeneral && <p role="alert" className={estilos.error}>{errorGeneral}</p>}
      <div className={estilos.acciones}>
        <button type="submit" className={estilos.enviar} disabled={guardando}>{etiquetaEnviar}</button>
        {alCancelar && (
          <button type="button" className={estilos.cancelar} onClick={alCancelar}>Cancelar</button>
        )}
      </div>
    </form>
  );
}

FormularioPeriodo.propTypes = {
  id: PropTypes.string.isRequired,
  titulo: PropTypes.string.isRequired,
  inicial: PropTypes.shape({ fechaInicio: PropTypes.string, fechaFin: PropTypes.string }),
  etiquetaEnviar: PropTypes.string.isRequired,
  alGuardar: PropTypes.func.isRequired,
  alCancelar: PropTypes.func,
};
