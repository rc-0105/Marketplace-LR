import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { limpiarSesion, suscribirExpiracion } from '../services/sesion.js';
import { useSesion } from './useSesion.js';

/**
 * Solo maneja efectos de la sesión: no expone contexto ni pinta markup.
 * Debe vivir dentro del Router.
 */
export default function ProveedorSesion({ children }) {
  const navegar = useNavigate();
  const expiraEn = useSesion()?.expiraEn;

  useEffect(
    () => suscribirExpiracion(() => navegar('/inicio-sesion', { replace: true, state: { sesionExpirada: true } })),
    [navegar],
  );

  useEffect(() => {
    if (!expiraEn) {
      return undefined;
    }
    const temporizador = setTimeout(() => limpiarSesion('expirada'), new Date(expiraEn) - Date.now());
    return () => clearTimeout(temporizador);
  }, [expiraEn]);

  return children;
}
