import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { useSesion } from '../sesion/useSesion.js';
import estilos from './Encabezado.module.css';

/**
 * Encabezado común de todas las páginas con la marca y el enlace al inicio.
 * Con sesión ofrece "Mi cuenta" y "Cerrar sesión", que delega en `alCerrarSesion`;
 * al anfitrión además le ofrece "Mis publicaciones".
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
        <div className={estilos.acciones}>
          {sesion.usuario.rol === 'ANFITRION' && (
            <Link to="/anfitrion/publicaciones" className={estilos.enlace}>Mis publicaciones</Link>
          )}
          <Link to="/mi-cuenta" className={estilos.enlace}>Mi cuenta</Link>
          <button type="button" className={estilos.cerrarSesion} onClick={cerrar} disabled={cerrando}>
            Cerrar sesión
          </button>
        </div>
      )}
    </header>
  );
}

Encabezado.propTypes = {
  alCerrarSesion: PropTypes.func.isRequired,
};
