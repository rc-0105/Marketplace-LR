import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link, NavLink } from 'react-router-dom';
import CampanaNotificaciones from '../../modules/notificaciones/components/CampanaNotificaciones.jsx';
import { homeDe } from '../sesion/rutasPorRol.js';
import { useSesion } from '../sesion/useSesion.js';
import Icono from './Icono.jsx';
import estilos from './Encabezado.module.css';

// Enlaces de navegación de cada rol; el administrador llega desde aquí a todas sus secciones.
const ENLACES_POR_ROL = {
  HUESPED: [{ ruta: '/huesped/reservas', texto: 'Mis reservas', icono: 'reservas' }],
  ANFITRION: [{ ruta: '/anfitrion/publicaciones', texto: 'Mis publicaciones', icono: 'publicaciones' }],
  ADMINISTRADOR: [
    { ruta: '/administrador/publicaciones', texto: 'Revisar publicaciones', icono: 'revisar' },
    { ruta: '/administrador/moderacion', texto: 'Moderación', icono: 'moderacion' },
    { ruta: '/administrador/resenas', texto: 'Reseñas', icono: 'resenas' },
    { ruta: '/administrador/usuarios', texto: 'Usuarios', icono: 'usuarios' },
    { ruta: '/administrador/auditoria', texto: 'Auditoría', icono: 'auditoria' },
  ],
};

function claseDeEnlace({ isActive }) {
  return isActive ? `${estilos.enlace} ${estilos.activo}` : estilos.enlace;
}

/**
 * Encabezado común de todas las páginas con la marca: sin sesión lleva al inicio
 * público y con sesión al panel del rol, desde cualquier pantalla.
 * Con sesión ofrece "Mi cuenta" y "Cerrar sesión", que delega en `alCerrarSesion`;
 * al huésped además le ofrece "Mis reservas", al anfitrión "Mis publicaciones" y al administrador
 * "Revisar publicaciones" y las demás secciones de administración. Con sesión, cualquier rol ve además la campana de notificaciones,
 * junto a "Cerrar sesión".
 */
export default function Encabezado({ alCerrarSesion }) {
  const sesion = useSesion();
  const [cerrando, setCerrando] = useState(false);
  const panel = sesion ? homeDe(sesion.usuario.rol) : null;
  const enlaces = sesion ? ENLACES_POR_ROL[sesion.usuario.rol] ?? [] : [];

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
      {!sesion && (
        <div className={estilos.acciones}>
          <Link to="/inicio-sesion" className={estilos.ingresar}>
            <Icono nombre="cuenta" className={estilos.icono} />
            Iniciar sesión
          </Link>
          <Link to="/registro" className={estilos.registrarse}>Crear cuenta</Link>
        </div>
      )}
      {sesion && (
        <div className={estilos.acciones}>
          <nav className={estilos.navegacion} aria-label="Navegación principal">
            {enlaces.map((enlace) => (
              <NavLink key={enlace.ruta} to={enlace.ruta} className={claseDeEnlace}>
                <Icono nombre={enlace.icono} className={estilos.icono} />
                {enlace.texto}
              </NavLink>
            ))}
            <NavLink to="/mi-cuenta" className={claseDeEnlace}>
              <Icono nombre="cuenta" className={estilos.icono} />
              Mi cuenta
            </NavLink>
          </nav>
          <span className={estilos.separador} aria-hidden="true" />
          <CampanaNotificaciones />
          <button type="button" className={estilos.cerrarSesion} onClick={cerrar} disabled={cerrando}>
            <Icono nombre="salir" className={estilos.icono} />
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
