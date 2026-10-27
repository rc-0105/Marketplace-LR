import PropTypes from 'prop-types';
import { Navigate } from 'react-router-dom';
import { homeDe } from './rutasPorRol.js';
import { useSesion } from './useSesion.js';

/** Muestra el contenido solo sin sesión; con sesión lleva al panel del rol. */
export default function SoloInvitados({ children }) {
  const sesion = useSesion();

  return sesion ? <Navigate to={homeDe(sesion.usuario.rol)} replace /> : children;
}

SoloInvitados.propTypes = {
  children: PropTypes.node.isRequired,
};
