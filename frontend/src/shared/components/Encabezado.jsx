import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { useSesion } from '../sesion/useSesion.js';
import estilos from './Encabezado.module.css';

/**
 * Encabezado común de todas las páginas con la marca y el enlace al inicio.
 * Con sesión ofrece "Cerrar sesión", que delega en `alCerrarSesion`.
 */
export default function Encabezado({ alCerrarSesion }) {
  const sesion = useSesion();
  const [cerrando, setCerrando] = useState(false);

  async function cerrar() {
    setCerrando(true);
    try {
      await alCerrarSesion();
    } finally {
      setCerrando(false);
    }
  }

  return (
    <header className={estilos.encabezado}>
      <Link to="/" className={estilos.marca} aria-label="Inversiones LR, ir al inicio">
        <span className={estilos.sello} aria-hidden="true">LR</span>
        Inversiones LR
      </Link>
      {sesion && (
        <button type="button" className={estilos.cerrarSesion} onClick={cerrar} disabled={cerrando}>
          Cerrar sesión
        </button>
      )}
    </header>
  );
}

Encabezado.propTypes = {
  alCerrarSesion: PropTypes.func.isRequired,
};
