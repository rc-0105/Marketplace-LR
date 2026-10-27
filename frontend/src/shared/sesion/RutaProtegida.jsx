import PropTypes from 'prop-types';
import { Navigate } from 'react-router-dom';
import { homeDe } from './rutasPorRol.js';
import { useSesion } from './useSesion.js';

/** Deja pasar solo al rol indicado; no pinta markup propio. */
export default function RutaProtegida({ rol, children }) {
  const sesion = useSesion();

  if (!sesion) {
    return <Navigate to="/inicio-sesion" replace />;
  }
  if (sesion.usuario.rol !== rol) {
    return <Navigate to={homeDe(sesion.usuario.rol)} replace />;
  }
  return children;
}

RutaProtegida.propTypes = {
  rol: PropTypes.string.isRequired,
  children: PropTypes.node.isRequired,
};
