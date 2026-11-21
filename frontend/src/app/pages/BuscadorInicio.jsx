import PropTypes from 'prop-types';
import { useState } from 'react';
import estilos from './BuscadorInicio.module.css';

function hoy() {
  const fecha = new Date();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

function validar({ ciudad, llegada, salida }) {
  if (!ciudad.trim()) return 'Indica a dónde quieres ir.';
  if (!llegada || !salida) return 'Indica las fechas de llegada y salida.';
  if (salida <= llegada) return 'La salida debe ser posterior a la llegada.';
  return '';
}

/** Barra de búsqueda del Inicio: dónde, fechas y cuántos; entrega los criterios ya validados. */
export default function BuscadorInicio({ alBuscar, buscando }) {
  const [datos, setDatos] = useState({ ciudad: '', llegada: '', salida: '', capacidad: '' });
  const [error, setError] = useState('');

  function cambiar(evento) {
    const { name, value } = evento.target;
    setDatos((actuales) => ({ ...actuales, [name]: value }));
  }

  function enviar(evento) {
    evento.preventDefault();
    const mensaje = validar(datos);
    setError(mensaje);
    if (mensaje) return;
    alBuscar({
      ciudad: datos.ciudad.trim(),
      llegada: datos.llegada,
      salida: datos.salida,
      ...(datos.capacidad ? { capacidad: datos.capacidad } : {}),
    });
  }

  return (
    <div className={estilos.contenedor}>
      <form className={estilos.buscador} onSubmit={enviar} noValidate aria-label="Buscar alojamientos">
        <div className={estilos.campo}>
          <label htmlFor="inicio-ciudad">Dónde</label>
          <input id="inicio-ciudad" name="ciudad" value={datos.ciudad} onChange={cambiar}
            placeholder="Explora destinos" autoComplete="off" />
        </div>
        <div className={estilos.campo}>
          <label htmlFor="inicio-llegada">Llegada</label>
          <input id="inicio-llegada" name="llegada" type="date" min={hoy()} value={datos.llegada} onChange={cambiar} />
        </div>
        <div className={estilos.campo}>
          <label htmlFor="inicio-salida">Salida</label>
          <input id="inicio-salida" name="salida" type="date" min={datos.llegada || hoy()} value={datos.salida}
            onChange={cambiar} />
        </div>
        <div className={estilos.campo}>
          <label htmlFor="inicio-capacidad">Quién</label>
          <input id="inicio-capacidad" name="capacidad" type="number" min="1" value={datos.capacidad}
            onChange={cambiar} placeholder="¿Cuántos?" />
        </div>
        <button type="submit" className={estilos.buscar} disabled={buscando} aria-label="Buscar">
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor"
            strokeWidth="2.4" strokeLinecap="round">
            <circle cx="11" cy="11" r="6.5" />
            <path d="M16 16l4.5 4.5" />
          </svg>
        </button>
      </form>
      {error && <p role="alert" className={estilos.error}>{error}</p>}
    </div>
  );
}

BuscadorInicio.propTypes = {
  alBuscar: PropTypes.func.isRequired,
  buscando: PropTypes.bool,
};
