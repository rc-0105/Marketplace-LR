import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link, useLocation } from 'react-router-dom';
import { homeDe } from '../sesion/rutasPorRol.js';
import { useSesion } from '../sesion/useSesion.js';
import estilos from './Encabezado.module.css';

/**
 * Encabezado común de todas las páginas con la marca: sin sesión lleva al inicio
 * público y con sesión al panel del rol. Con sesión, fuera del panel, ofrece además
 * "Volver a mi panel" para salir de cualquier pantalla.
 * Con sesión ofrece "Mi cuenta" y "Cerrar sesión", que delega en `alCerrarSesion`;
 * al anfitrión además le ofrece "Mis publicaciones" y al administrador "Revisar publicaciones".
 */
export default function Encabezado({ alCerrarSesion }) {
  const sesion = useSesion();
  const { pathname } = useLocation();
  const [cerrando, setCerrando] = useState(false);
  const panel = sesion ? homeDe(sesion.usuario.rol) : null;

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
      <Link to={panel ?? '/'} className={estilos.marca}
        aria-label={sesion ? 'Inversiones LR, ir a tu panel' : 'Inversiones LR, ir al inicio'}>
        <span className={estilos.sello} aria-hidden="true">LR</span>
        Inversiones LR
      </Link>
      {sesion && (
        <div className={estilos.acciones}>
          {pathname !== panel && (
            <Link to={panel} className={estilos.volver}>
              <span aria-hidden="true">←</span> Volver a mi panel
            </Link>
          )}
          {sesion.usuario.rol === 'ANFITRION' && (
            <Link to="/anfitrion/publicaciones" className={estilos.enlace}>Mis publicaciones</Link>
          )}
          {sesion.usuario.rol === 'ADMINISTRADOR' && (
            <Link to="/administrador/publicaciones" className={estilos.enlace}>Revisar publicaciones</Link>
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
