import { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useSesion } from '../../../shared/sesion/useSesion.js';
import { fijarNoLeidas } from '../services/contadorNotificaciones.js';
import { listarNotificaciones } from '../services/notificacionesService.js';
import { useNoLeidas } from '../services/useNoLeidas.js';
import estilos from './CampanaNotificaciones.module.css';

/**
 * Campana del encabezado con el número de notificaciones sin leer; lleva a /notificaciones.
 * Recarga el contador al aparecer la sesión y en cada cambio de ruta, sin sondeo periódico.
 * Si la consulta falla no muestra error ni contador.
 */
export default function CampanaNotificaciones() {
  const sesion = useSesion();
  const { pathname } = useLocation();
  const noLeidas = useNoLeidas();
  const token = sesion?.token;

  useEffect(() => {
    if (!token) return undefined;
    let vigente = true;
    listarNotificaciones()
      .then((respuesta) => vigente && fijarNoLeidas(respuesta?.noLeidas))
      .catch(() => vigente && fijarNoLeidas(0));
    return () => {
      vigente = false;
    };
  }, [token, pathname]);

  return (
    <Link to="/notificaciones" className={estilos.campana}
      aria-label={noLeidas > 0 ? `Notificaciones, ${noLeidas} sin leer` : 'Notificaciones'}>
      <svg className={estilos.icono} viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
        <path d="M12 3a6 6 0 0 0-6 6v3.6L4.3 15.4A1 1 0 0 0 5.2 17h13.6a1 1 0 0 0 .9-1.6L18 12.6V9a6 6 0 0 0-6-6Z"
          fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
        <path d="M10 20a2 2 0 0 0 4 0" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
      {noLeidas > 0 && <span className={estilos.contador} aria-hidden="true">{noLeidas}</span>}
    </Link>
  );
}
