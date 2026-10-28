import PropTypes from 'prop-types';
import { Navigate } from 'react-router-dom';
import { homeDe } from './rutasPorRol.js';
import { useSesion } from './useSesion.js';

/** Exige sesión y, si se indica `rol`, solo deja pasar a ese rol; no pinta markup propio. */
export default function RutaProtegida({ rol, children }) {
  const sesion = useSesion();

  if (!sesion) {
    return <Navigate to="/inicio-sesion" replace />;
  }
  if (rol && sesion.usuario.rol !== rol) {
    return <Navigate to={homeDe(sesion.usuario.rol)} replace />;
  }
  return children;
}

RutaProtegida.propTypes = {
  rol: PropTypes.string,
  children: PropTypes.node.isRequired,
};
